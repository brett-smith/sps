package com.sshtools.forker.plugin.shell;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.BLUE;
import static org.fusesource.jansi.Ansi.Color.CYAN;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.Color.YELLOW;

import java.util.Stack;

import org.apache.commons.cli.CommandLine;
import org.fusesource.jansi.Ansi.Color;

import com.sshtools.forker.plugin.api.PluginProgressMonitor;

public final class ConsoleProgressMonitor implements PluginProgressMonitor {

	/**
	 * 
	 */
	private final PluginShell pluginShell;

	class Message {
		long progress;
		String text;
	}

	private String blocks = "";
	private CommandLine cmdline;
	private Stack<Message> messages = new Stack<>();
	private String spaces = "";
	private Stack<Long> tasks = new Stack<>();

	{
		for (int i = 0; i < 1024; i++) {
			spaces += " ";
			blocks += "◼";
		}
	}

	public ConsoleProgressMonitor(PluginShell pluginShell, CommandLine cmdline) {
		this.pluginShell = pluginShell;
		this.cmdline = cmdline;
	}

	@Override
	public void changeTotal(long newTotalTasks) {
		if (cmdline.hasOption('b'))
			return;
		this.tasks.push(newTotalTasks);
		Message prog = this.messages.peek();
		progress(prog.progress, prog.text);
	}

	@Override
	public void end() {
		if (cmdline.hasOption('b'))
			return;
		tasks.pop();
		messages.pop();
		if (!messages.isEmpty()) {
			Message msg = messages.peek();
			progress(msg.progress, msg.text);
		}
		System.out.print(ansi().eraseLine());
	}

	@Override
	public void message(MessageType type, String message) {
		if (cmdline.hasOption('q'))
			return;
		if (tasks.size() == 0 && !cmdline.hasOption('b'))
			throw new IllegalStateException("Messages must be inside a task or the --no-progress option must be used.");

		if (this.pluginShell.isAnsi())
			System.out.print(ansi().eraseLine());
		else {
			System.out.print(spaces.substring(0, this.pluginShell.getTerminalWidth() - 1) + "\r");
		}

		if (type == MessageType.ERROR || type == MessageType.WARNING
				|| (type == MessageType.INFO && cmdline.hasOption('v'))
				|| type == MessageType.DEBUG && cmdline.hasOption('d')) {

			Color col;
			switch (type) {
			case INFO:
				col = BLUE;
				break;
			case WARNING:
				col = YELLOW;
				break;
			case ERROR:
				col = RED;
				break;
			default:
				col = CYAN;
				break;
			}
			this.pluginShell.printMessage(type, message, col);
		}
		if (!cmdline.hasOption('b')) {
			Message prog = this.messages.peek();
			progress(prog.progress, prog.text == null ? "" : prog.text);
		}
	}

	@Override
	public void progress(long progress, String message) {
		if (cmdline.hasOption('b'))
			return;
		long total = this.tasks.peek();
		if (progress > total)
			progress = total;
		Message prog = this.messages.peek();
		prog.progress = progress;
		prog.text = message;
		int pc = (int) ((double) progress / (double) total * 100d);
		int space = this.pluginShell.getTerminalWidth() - 7;
		int barSpace = (space - 3) / 2;
		int msgSpace = space - barSpace - 6;
		if (message.length() > msgSpace)
			message = message.substring(0, msgSpace);
		int barWidth = (int) (((double) progress / (double) total) * (double) barSpace);
		String bar = blocks.substring(0, barWidth);
		String block = spaces.substring(0, barSpace - barWidth);
		if (this.pluginShell.isAnsi())
			System.out.print(String.format("[%s%3d%%%s] (%s%s%s%s) %s%s%s", ansi().fg(BLUE), pc, ansi().fgDefault(),
					ansi().fg(Color.MAGENTA), bar, block, ansi().fgDefault(), message, ansi().eraseLine(),
					ansi().cursorToColumn(0)));
		else
			System.out.print(String.format("[%3d%%] (%s%s) %s\r", pc, bar, block, message));
	}

	@Override
	public void start(long totalTasks) {
		if (cmdline.hasOption('b'))
			return;
		tasks.push(totalTasks);
		messages.push(new Message());
	}
}