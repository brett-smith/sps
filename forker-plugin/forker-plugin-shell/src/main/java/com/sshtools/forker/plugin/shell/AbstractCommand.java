package com.sshtools.forker.plugin.shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;

import com.sshtools.forker.plugin.api.PluginArchive;
import com.sshtools.forker.plugin.api.PluginComponent;
import com.sshtools.forker.plugin.api.PluginComponentId;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.ResolutionState;

public abstract class AbstractCommand implements Command {

	protected static String spaces;
	protected static String dashes;

	static {
		StringBuilder b = new StringBuilder();
		StringBuilder d = new StringBuilder();
		for (int i = 0; i < 512; i++) {
			b.append(' ');
			d.append('-');
		}
		spaces = b.toString();
		dashes = d.toString();
	}

	protected PluginManager manager;
	protected Terminal terminal;

	@Override
	public void attach(Terminal terminal) {
		this.terminal = terminal;
	}

	@Override
	public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) throws IOException {
	}

	public abstract void exec(PluginProgressMonitor monitor, CommandLine commandLine) throws IOException;

	@Override
	public Options getOptions() {
		Options opts = buildOptions();
		if (opts == null) {
			opts = new Options();
		}
		return opts.addOption(new Option("?", "help", false, "Help for this command."));
	}

	public int getTerminalWidth() {
		return terminal.getWidth() < 1 ? 80 : terminal.getWidth();
	}

	public boolean isTerminal() {
		return terminal.getWidth() > 0;
	}

	@Override
	public void init(PluginManager manager) {
		this.manager = manager;
	}

	@Override
	public void run(String name, PluginProgressMonitor monitor, CommandLine commandLine) throws IOException {
		if (commandLine.hasOption('?')) {
			commandHelp(name, this);
		} else
			exec(monitor, commandLine);
	}

	protected Options buildOptions() {
		return new Options();
	}

	protected String colourise(Color col, String text) {
		return String.format("%s%s%s", Ansi.ansi().fg(col), text, Ansi.ansi().fgDefault());
	}

	protected void commandHelp(String c, Command cmd) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.setWidth(getTerminalWidth() == 0 ? 880 : getTerminalWidth());
		formatter.printHelp(getClass().getName(), cmd.getDescription(), getOptions(), null, true);
	}

	protected List<String> expand(List<String> args, Collection<? extends PluginComponent<?>> choices) {
		List<String> expanded = new ArrayList<>(args.size());
		for (String a : args) {
			if (a.indexOf('?') == -1 && a.indexOf('*') == -1) {
				expanded.add(a);
			} else {
				StringBuilder pattern = new StringBuilder("^");
				for (char ch : a.toCharArray()) {
					if (ch == '*')
						pattern.append(".*");
					else if (ch == '?')
						pattern.append(".?");
					else
						pattern.append(ch);
				}
				pattern.append("$");
				for (PluginComponent<?> c : choices) {
					if (c.getComponentId().toString().matches(pattern.toString())) {
						expanded.add(c.getComponentId().toString());
					}
				}
			}
		}
		return expanded;
	}

	protected Color getStateColour(ResolutionState state) {
		switch (state) {
		case UNRESOLVED:
			return Color.CYAN;
		case PARTIAL:
			return Color.MAGENTA;
		case SATISFACTORY:
			return Color.BLUE;
		case STARTED:
			return Color.YELLOW;
		case ERRORED:
			return Color.RED;
		default:
			return Color.GREEN;
		}
	}

	protected void printComponent(PluginComponentId id, PluginProgressMonitor progress, String... message)
			throws IOException {
		printComponent(id, progress, false, message);
	}

	protected void printComponent(PluginComponentId id, PluginProgressMonitor progress, boolean verbose,
			String... message) throws IOException {
		printComponent(id, progress, 0, verbose, message);
	}

	protected void printComponent(PluginComponentId id, PluginProgressMonitor progress, int indent, boolean verbose,
			String... message) throws IOException {
		printComponent(manager.getResolutionContext().getDependencyTree().get(id, PluginComponent.class), progress,
				indent, verbose, message);
	}

	protected void printComponent(PluginComponent<?> a, PluginProgressMonitor progress, String... message)
			throws IOException {
		printComponent(a, progress, false, message);
	}

	protected void printComponent(PluginComponent<?> a, PluginProgressMonitor progress, boolean verbose,
			String... message) throws IOException {
		printComponent(a, progress, 0, verbose, message);
	}

	protected void printComponent(PluginComponent<?> a, PluginProgressMonitor progress, int indent, boolean verbose,
			String... message) throws IOException {
		if (a == null)
			return;
		String spacesstr = indent < spaces.length() ? spaces.substring(0, indent) : spaces;
		if (verbose) {
			String state = a.getState().name();
			String code2 = a.getScope().name().substring(0, 1);
			String code3 = "-";
			if (a instanceof PluginArchive) {
				if (((PluginArchive) a).getEmbedder() != null)
					code2 = "E";
				code3 = ((PluginArchive) a).getComponentId().getType().name().substring(0, 1);
			}
			int w = getTerminalWidth();
			int msgWidth = w - 24 - indent;
			String msg = a.getComponentId().toString();
			if (message.length > 0) {
				msg += " " + String.join(" ", message);
			}
			if (msg.length() > msgWidth)
				msg = msg.substring(0, msgWidth);
			System.out.println(String.format("[%s%-15s%s] [%3s] %s%-60s", Ansi.ansi().fg(getStateColour(a.getState())),
					state, Ansi.ansi().fgDefault(), (a.getComponentId().isOptional() ? "-" : "*") + code2 + code3,
					spacesstr, msg));
		} else {
			if (message.length > 0) {
				System.out.println(spacesstr + a.getComponentId().toString() + " " + String.join(" ", message));
			} else
				System.out.println(spacesstr + a.getComponentId().toString());
		}
	}

	protected void printInfoLine(int indent, String title, Object val) {
		printInfoLine(indent, title, val, null);
	}

	protected void printInfoLine(int indent, String title, Object val, Color col) {
		int labelWidth = getTerminalWidth() / 3;
		int msgWidth = getTerminalWidth() - labelWidth - 3;
		String labelText = spaces.substring(0, indent) + title;
		if (labelText.length() > labelWidth)
			labelText = labelText.substring(0, labelWidth);
		String text = String.valueOf(val);
		String msg = text;
		if (msg.length() > msgWidth)
			msg = msg.substring(0, msgWidth);
		if (col == null)
			System.out.println(String.format("%s%-" + labelWidth + "s%s : %s", Ansi.ansi().bold(), labelText,
					Ansi.ansi().boldOff(), msg));
		else
			System.out.println(String.format("%s%-" + labelWidth + "s%s : %s%s%s", Ansi.ansi().bold(), labelText,
					Ansi.ansi().boldOff(), Ansi.ansi().fg(col), msg, Ansi.ansi().fgDefault()));
		text = text.substring(msg.length());
		while (text.length() > 0) {
			msg = text;
			if (msg.length() > msgWidth)
				msg = msg.substring(0, msgWidth);
			System.out.println(spaces.substring(0, labelWidth + 3) + msg);
			text = text.substring(msg.length());
		}
	}
}
