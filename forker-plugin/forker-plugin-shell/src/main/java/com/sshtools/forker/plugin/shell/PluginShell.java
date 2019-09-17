package com.sshtools.forker.plugin.shell;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.RED;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.fusesource.jansi.Ansi.Color;
import org.fusesource.jansi.AnsiConsole;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

import com.sshtools.forker.plugin.DefaultPluginManager;
import com.sshtools.forker.plugin.FeatureRemote;
import com.sshtools.forker.plugin.Hashing;
import com.sshtools.forker.plugin.api.ConflictStrategy;
import com.sshtools.forker.plugin.api.InstallMode;
import com.sshtools.forker.plugin.api.PluginEntryPointExecutor;
import com.sshtools.forker.plugin.api.PluginManager;
import com.sshtools.forker.plugin.api.PluginProgressMonitor;
import com.sshtools.forker.plugin.api.PluginProgressMonitor.MessageType;
import com.sshtools.forker.plugin.api.PluginRemote;
import com.sshtools.forker.plugin.maven.MavenProjectRemote;
import com.sshtools.forker.plugin.maven.MavenRemote;

public class PluginShell implements PluginEntryPointExecutor {

	static final String PREF_AUTOSTART = "autostart";
	static final String PREF_MODE = "mode";
	static final String PREF_CONFLICT_STRATEGY = "conflictStrategy";

	final static Preferences SYS_PREFS = getSysPrefs();
	final static Preferences USER_PREFS = getUserPrefs();

	public static PluginRemote createRemote(String type, String remote, int weight, boolean enabled)
			throws IOException {
		PluginRemote pluginRemote = null;
		if (type == null) {
			try {
				URL u = new URL(remote);
				URL feat = new URL(u, "features.txt");
				URLConnection conx = feat.openConnection();
				try {
					conx.getContent();
					pluginRemote = new FeatureRemote(remote, weight);
				} catch (FileNotFoundException fnfe) {
					pluginRemote = new MavenRemote(remote, weight);
				}
			} catch (MalformedURLException murle) {
				File file = new File(FilenameUtils.normalize(remote)).getAbsoluteFile();
				if (new File(file, "pom.xml").exists()) {
					pluginRemote = new MavenProjectRemote(file.getPath(), weight);
				} else if (new File(file, "features.txt").exists()) {
					pluginRemote = new FeatureRemote(file.toURI().toString(), weight);
				} else
					throw new IllegalArgumentException(
							"Could not detect remote type. Please specify using a corresponding --remote-type argument.");
			}
		} else {
			if (type.equals("maven") || type.equals(MavenRemote.class.getName()))
				pluginRemote = new MavenRemote(remote, weight);
			else if (type.equals("maven-project") || type.equals(MavenProjectRemote.class.getName()))
				pluginRemote = new MavenProjectRemote(remote, weight);
			else if (type.equals("feature") || type.equals(FeatureRemote.class.getName()))
				pluginRemote = new FeatureRemote(remote, weight);
			else
				throw new IllegalArgumentException("Unknown remote type.");
		}
		pluginRemote.setEnabled(enabled);
		return pluginRemote;
	}

	public static void main(String[] args) throws Exception {

		AnsiConsole.systemInstall();

		PluginShell boot = new PluginShell(args);
		boot.addCommand("available", new Available());
		boot.addCommand("installed", new Installed());
		boot.addCommand("ls", new Ls());
		boot.addCommand("resolve", new Resolve());
		boot.addCommand("start", new Start());
		boot.addCommand("state", new State());
		boot.addCommand("info", new Info());
		boot.addCommand("close", new Close());
		boot.addCommand("cls", new Cls());
		boot.addCommand("clean", new Clean());
		boot.addCommand("remotes", new Remote());
		boot.addCommand("trace", new Trace(boot));
		boot.addCommand("install", new Install());
		boot.addCommand("uninstall", new Uninstall());
		boot.addCommand("add-remote", new AddRemote());
		boot.addCommand("remove-remote", new RemoveRemote());
		boot.addCommand("plugins", new Plugins());
		boot.addCommand("classpath", new Classpath());
		boot.addCommand("conflicts", new Conflicts());
		boot.addCommand("autostart", new Autostart());
		boot.addCommand("mode", new Mode());
		boot.addCommand("help", new Help(boot));
		boot.main();
	}

	public static List<String> parseQuotedString(String string) {
		string = string.trim();
		boolean inQuotes = false;
		boolean escaped = false;
		List<String> args = new LinkedList<>();
		StringBuilder word = null;
		for (char ch : string.toCharArray()) {
			if (ch == '"' && !escaped) {
				inQuotes = !inQuotes;
			} else if (ch == ' ' && !inQuotes && !escaped) {
				if (word != null)
					args.add(word.toString());
				word = null;
			} else if (ch == '\\' && !escaped) {
				escaped = true;
			} else {
				escaped = false;
				if (word == null && (inQuotes || ch != ' '))
					word = new StringBuilder();
				if (word != null)
					word.append(ch);
			}
		}
		if (escaped)
			throw new IllegalArgumentException("Incomplete escape.");
		if (inQuotes)
			throw new IllegalArgumentException("Unbalanced quotes.");
		if (word != null)
			args.add(word.toString());
		return args;
	}

	public static void removeRemote(PluginRemote remote, boolean system) {
		try {
			Preferences remotesNode = (system ? SYS_PREFS : USER_PREFS).node("remotes");
			Preferences remoteNode = remotesNode.node(Hashing.hash(remote.getId()));
			remoteNode.removeNode();
		} catch (BackingStoreException bse) {
			throw new IllegalStateException("Failed to remove remote.", bse);
		}

	}

	public static void saveRemote(PluginRemote remote, boolean system) {
		Preferences remotesNode = (system ? SYS_PREFS : USER_PREFS).node("remotes");
		Preferences remoteNode = remotesNode.node(Hashing.hash(remote.getId()));
		remoteNode.put("type", remote.getClass().getName());
		remoteNode.put("id", remote.getId());
		remoteNode.put("enabled", String.valueOf(remote.isEnabled()));
		remoteNode.putInt("weight", remote.getWeight());
	}

	static Preferences getSysPrefs() {
		//
		if (SystemUtils.IS_OS_UNIX) {
			File dir = new File(System.getProperty("java.util.prefs.systemRoot", "/etc/.java/.systemPrefs"));
			if ((!dir.exists() && !dir.mkdirs()) || !dir.canWrite()) {
				/* Will not be able to write, so treat system as local */
				return getUserPrefs();
			}
		}
		return Preferences.systemNodeForPackage(PluginShell.class);
	}

	static Preferences getUserPrefs() {
		return Preferences.userNodeForPackage(PluginShell.class);
	}

	static List<PluginRemote> loadRemotes() {
		List<PluginRemote> l = new ArrayList<>();
		loadRemotes(SYS_PREFS.node("remotes"), l);
		if (!SYS_PREFS.equals(USER_PREFS))
			loadRemotes(USER_PREFS.node("remotes"), l);
		return l;
	}

	static void loadRemotes(Preferences prefs, List<PluginRemote> remotes) {
		try {
			for (String n : prefs.childrenNames()) {
				Preferences p = prefs.node(n);
				remotes.add(createRemote(p.get("type", null), p.get("id", "sink://"), p.getInt("weight", 0),
						p.getBoolean("enabled", true)));
			}
		} catch (BackingStoreException | IOException bse) {
			throw new IllegalStateException("Could not load remote node preferences.", bse);
		}
	}

	private String[] cmdlineArgs;
	private Map<String, Command> commands = new HashMap<>();

	private Exception lastError;

	private BlockingQueue<Runnable> mainJobs = new LinkedBlockingQueue<>();

	private Thread mainThread;

	private boolean exit;
	private PluginManager manager;
	private String prompt;
	private PluginProgressMonitor progressMonitor;

	private Terminal terminal;

	private boolean traces;
	private boolean runningMain;

	public PluginShell(String[] cmdlineArgs) throws IOException {
		this.cmdlineArgs = cmdlineArgs;
		createManager();
	}

	public void addCommand(String key, Command command) {
		if (commands.containsKey(key))
			throw new IllegalArgumentException(String.format("Command %s already registered.", key));
		command.init(manager);
		commands.put(key, command);
	}

	@Override
	public void exec(Runnable runnable) throws InterruptedException {
		if (runningMain)
			throw new IllegalStateException(
					"Another plugin is already running as an entry point. Only one is allowed.");
		mainJobs.put(runnable);
	}

	public Map<String, Command> getCommands() {
		return commands;
	}

	public boolean isTraces() {
		return traces;
	}

	public void main() throws Exception {
		CommandLineParser parser = new DefaultParser();

		Options opts = new Options();
		opts.addOption("n", "no-resolve-on-start", false, "Do not perform an initial resolve on startup. ");
		opts.addOption("a", "autostart-default", true, "The default value for when autostart is not specified.");
		opts.addOption("i", "include", true, "Regular expression for plugins that should be included.");
		opts.addOption("e", "exclude", true, "Regular expression for plugins that should be excluded.");
		opts.addOption("v", "verbose", false, "Enable verbose output.");
		opts.addOption("r", "remote", true,
				"Add a remote URL to obtain new archives from. Some attempt will be made to deduce the type of remote given it's URL, or you can supply the optional --remote-type argument.");

		opts.addOption("t", "remote-type", true,
				"Only used when adding a remote, may be one of maven, maven-project or feature.");
		opts.addOption("m", "install-mode", true,
				"Installation mode. This currently applies to installing from remotes that are local file, and determines whether files are copied or linked. Possible values are "
						+ Arrays.asList(InstallMode.values()).stream().map(Object::toString)
								.collect(Collectors.joining(",")));
		opts.addOption("c", "conflict-strategy", true,
				"Conflict strategy. How to deal with differing versions of the same artifact. Possible values are "
						+ Arrays.asList(ConflictStrategy.values()).stream().map(Object::toString)
								.collect(Collectors.joining(",")));
		opts.addOption("p", "prompt", true, "The interactive shell prompt.");
		opts.addOption("w", "remote-weight", true,
				"Only used when adding a remote, an integer value used for sorting.");
		opts.addOption("d", "debug", false, "Enable debug output.");
		opts.addOption("q", "quiet", false, "Disable all message output (this does not include progress bars)");
		opts.addOption("b", "no-progress", false, "Disable progress bars.");
		opts.addOption("s", "no-shell", false,
				"Do not start an interactive shell, exiting if there are no entry point plugins started. If an entry is started, the runtime will continue until it exits.");
		opts.addOption("?", "help", false, "Show option and argument help.");

		CommandLine cmdline = parser.parse(opts, cmdlineArgs);
		if (cmdline.hasOption('v') || cmdline.hasOption('d')) {
			setTraces(true);
		}
		if (cmdline.hasOption('a')) {
			manager.setAutostart("true".equalsIgnoreCase(cmdline.getOptionValue('a'))
					|| "on".equalsIgnoreCase(cmdline.getOptionValue('a')));
		} else {
			manager.setAutostart(USER_PREFS.getBoolean(PREF_AUTOSTART, false));
		}
		if (cmdline.hasOption('m')) {
			manager.setInstallMode(InstallMode.valueOf(cmdline.getOptionValue('m')));
		} else {
			manager.setInstallMode(InstallMode.valueOf(USER_PREFS.get(PREF_MODE, InstallMode.AUTO.name())));
		}
		if (cmdline.hasOption('c')) {
			manager.setConflictStrategy(ConflictStrategy.valueOf(cmdline.getOptionValue('c')));
		} else {
			manager.setConflictStrategy(ConflictStrategy
					.valueOf(USER_PREFS.get(PREF_CONFLICT_STRATEGY, ConflictStrategy.USE_LATEST.name())));
		}
		setPrompt(cmdline.getOptionValue('p'));
		String[] vals = cmdline.getOptionValues('i');
		if (vals != null)
			for (String i : vals)
				manager.getPluginInclude().add(i);
		vals = cmdline.getOptionValues('e');
		if (vals != null)
			for (String i : vals)
				manager.getPluginExclude().add(i);

		String[] remoteIds = cmdline.getOptionValues('r');
		String[] typeIds = cmdline.getOptionValues('t');
		String[] remoteWeights = cmdline.getOptionValues('w');
		if (remoteIds != null && typeIds != null && typeIds.length != 1 && typeIds.length != remoteIds.length)
			throw new IllegalArgumentException(
					"If one or more --remote specifications are supplied, you may only support either zero, 1 or the same number of --remote-type arguments");
		if (remoteIds != null && remoteWeights != null && remoteWeights.length != 1
				&& remoteWeights.length != remoteIds.length)
			throw new IllegalArgumentException(
					"If one or more --remote specifications are supplied, you may only support either zero, 1 or the same number of --remote-weight arguments");
		if (remoteIds != null) {
			for (int i = 0; i < remoteIds.length; i++) {
				String type = null;
				int weight = 0;
				if (typeIds != null) {
					if (typeIds.length == 1)
						type = typeIds[0];
					else
						type = typeIds[i];
				}
				if (remoteWeights != null) {
					if (typeIds.length == 1)
						weight = Integer.parseInt(remoteWeights[0]);
					else
						weight = Integer.parseInt(remoteWeights[i]);
				}
				String remote = remoteIds[i];
				PluginRemote pluginRemote = createRemote(type, remote, weight, true);
				manager.addRemote(pluginRemote);

			}
		}

		/*
		 * Always create the line reader, so we can get at terminal attributes to use
		 * with fancy output
		 */
		LineReaderBuilder builder = LineReaderBuilder.builder();
		builder.variable(LineReader.HISTORY_FILE, new File(new File(System.getProperty("user.home")),
				PluginShell.class.getPackage().getName() + ".history"));
		builder.completer(new Completer() {
			@Override
			public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
				if (line.wordIndex() == 0) {
					for (String c : commands.keySet()) {
						if (c.startsWith(line.word()))
							candidates.add(new Candidate(c));
					}
				} else {
					Command cmd = commands.get(line.words().get(0));
					if (cmd != null) {
						try {
							cmd.complete(reader, line, candidates);
						} catch (IOException e) {
						}
					}
				}
			}
		});
		LineReader reader = builder.build();

		terminal = reader.getTerminal();
		for (Command cmd : commands.values())
			cmd.attach(terminal);

		if (cmdline.hasOption('?')) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(getTerminalWidth());
			formatter.printHelp(getClass().getSimpleName(), opts);
			System.exit(1);
		}

		progressMonitor = new ConsoleProgressMonitor(this, cmdline);
		mainThread = Thread.currentThread();

		if (cmdline.hasOption('s')) {
			manager.setEntryPointExecutor(new PluginEntryPointExecutor() {
				@Override
				public void exec(Runnable runnable) throws InterruptedException {
					runnable.run();
				}
			});
			try {
				/* Now we can resolve for the first time if configured to do so */
				if (!cmdline.hasOption('n')) {
					manager.resolve(progressMonitor);
				}

				if (cmdline.getArgList().size() > 0)
					parseCommand(parser, cmdline.getArgList());

			} catch (IOException | ParseException ioe) {
				lastError = ioe;
			} finally {
				exit = true;
				mainThread.interrupt();
			}
		} else {
			manager.setEntryPointExecutor(this);
			new Thread("PluginShell") {
				public void run() {
					try {
						if (cmdline.getArgList().isEmpty()) {
							interactiveShell(parser, cmdline, reader);
						} else {
							/* Now we can resolve for the first time if configured to do so */
							if (!cmdline.hasOption('n')) {
								manager.resolve(progressMonitor);
							}

							if (cmdline.getArgList().size() > 0)
								parseCommand(parser, cmdline.getArgList());
						}
					} catch (IOException | ParseException ioe) {
						lastError = ioe;
					} finally {
						exit = true;
						mainThread.interrupt();
					}
				}
			}.start();
			mainLoop();
		}

		if (lastError != null)
			throw lastError;
	}

	protected void mainLoop() {
		/*
		 * Now wait for jobs on the main thread. Some plugins, for example GUI
		 * applications on OSX may require that they are run on the main thread. To
		 * achieve this, we put the plugin shell itself on a new thread, an place any
		 * start events on a queue on the main thread
		 */
		while (!exit) {
			try {
				Runnable job = mainJobs.take();
				runningMain = true;
				try {
					job.run();
				} finally {
					runningMain = false;
				}
			} catch (InterruptedException ex) {
				break;
			}
		}
	}

	public void parseCommand(CommandLineParser parser, List<String> args) throws ParseException, IOException {
		CommandLine cmdline;
		String cmd = args.remove(0);
		Command command = this.commands.get(cmd);
		if (command == null)
			throw new IllegalArgumentException(String.format("No such command.", cmd));

		cmdline = parser.parse(command.getOptions(), args.toArray(new String[0]));
		command.run(cmd, progressMonitor, cmdline);
	}

	public void printMessage(MessageType type, String message, Color col) {
		int w = getTerminalWidth();
		String text = message;
		if (text.length() > w - 15)
			text = message.substring(0, w - 15);
		if (isAnsi())
			System.out.println(String.format("[%s%-10s%s] : %s%s", ansi().fg(col), type.name(), ansi().fgDefault(),
					text, ansi().eraseLine()));
		else
			System.out.println(String.format("[%-10s] : %s", type.name(), text));

		while (message.length() > 0) {
			message = message.substring(text.length());
			text = message;
			if (text.length() > w - 15)
				text = message.substring(0, w - 15);
			System.out.println(String.format("%15s%s", "", text));
		}
	}

	public String getPrompt() {
		if (prompt == null) {
			return ansi().bold().a("fps> ").boldOff().toString();
		}
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public void setTraces(boolean traces) {
		this.traces = traces;
	}

	protected void interactiveShell(CommandLineParser parser, CommandLine cmdline, LineReader reader)
			throws IOException {
		System.out.println(
				String.format("%sForker Plugin System%s", ansi().bold().fg(Color.GREEN), ansi().boldOff().fgDefault()));
		System.out.println(String.format("Interactive shell. Type %shelp%s for command list.\n",
				ansi().fg(Color.YELLOW), ansi().fgDefault()));

		if (getSysPrefs().equals(getUserPrefs())) {
			System.out.println(String.format(
					"\n%sWARNING: You do not have permission to save system-id changes such as persistent remotes. All changes will be written as your local user.\n%s",
					ansi().fg(Color.RED), ansi().fgDefault()));
		}

		/* Now we can resolve for the first time if configured to do so */
		if (!cmdline.hasOption('n')) {
			try {
				manager.resolve(progressMonitor);
			} catch (Exception e) {
				printMessage(MessageType.ERROR,
						"Failed initial resolution. Plugins and/or archives may not be resolved.", RED);
				if (traces)
					e.printStackTrace(System.out);
			}
		}

		while (true) {
			String line = null;
			try {
				line = reader.readLine(getPrompt());
				List<String> args = parseQuotedString(line);
				if (args.isEmpty())
					continue;
				try {
					parseCommand(parser, args);
				} catch (IllegalArgumentException iae) {
					printMessage(MessageType.ERROR, iae.getMessage(), RED);
					if (traces)
						iae.printStackTrace(System.out);
				} catch (Exception e) {
					printMessage(MessageType.ERROR, e.getMessage() == null ? "No message supplied." : e.getMessage(),
							RED);
					if (traces)
						e.printStackTrace(System.out);
				}
			} catch (UserInterruptException e) {
				// Ignore
			} catch (EndOfFileException e) {
				return;
			}
		}
	}

	protected void createManager() throws IOException {
		manager = new DefaultPluginManager();
		manager.setCleanOrphans(false);
		for (PluginRemote r : loadRemotes()) {
			try {
				manager.addRemote(r);
			} catch (IllegalArgumentException iae) {
				System.out.println("WARNING: " + iae.getMessage());
			}
		}
	}

	protected int getTerminalWidth() {
		int width = terminal.getWidth();
		if (width < 1)
			width = 80;
		return width;
	}

	protected boolean isAnsi() {
		// TODO better way?
		return terminal.getWidth() > 0;
	}
}
