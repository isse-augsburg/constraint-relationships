package isse.mbr.tools.execution;

import isse.mbr.parsing.MiniBrassCompiler;
import isse.mbr.parsing.MiniBrassCompiler.StdoutConsoleHandler;
import isse.mbr.parsing.MiniBrassParseException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The MiniBrass runner is responsible for executing branch-and-bound or other
 * searches that involve executing several MiniZinc processes
 * <p>
 * usage: minibrass constraintModel.mzn file.mbr [dataFiles.dzn]
 *
 * @author alexander
 */
public class MiniBrassRunner {
	private static final int RANDOM_SEED_LIMIT = Integer.MAX_VALUE / 2;
	private final MiniZincRunner miniZincRunner;
	private final MiniBrassCompiler miniBrassCompiler;
	private boolean writeIntermediateFiles;
	private boolean debug;
	private int initialRandomSeed;
	private boolean randomize;
	private boolean dominationSearch; // solution has to get strictly better (otherwise only have to not be worse)
	private Random randomSequence;
	private Integer timeoutInSeconds; // in milliseconds


	private File originalMiniZincFile;
	private List<MiniZincSolution> allSolutions;
	private static Logger logger;

	public MiniBrassRunner() {
		miniZincRunner = new MiniZincRunner();
		MiniZincConfiguration config = new MiniZincConfiguration();
		config.setUseAllSolutions(false);

		miniZincRunner.setConfiguration(config);

		miniBrassCompiler = new MiniBrassCompiler();
		writeIntermediateFiles = true;
		randomize = false;
		initialRandomSeed = 1337;
		dominationSearch = true;
		debug = false;
		timeoutInSeconds = null;
	}

	public MiniBrassRunner(MiniZincConfiguration configuration) {
		this();
		miniZincRunner.setConfiguration(configuration);
	}

	public MiniZincSolution executeBranchAndBound(File miniZincFile, File miniBrassFile, List<File> dataFiles)
			throws IOException, MiniBrassParseException {
		// initialize variables
		originalMiniZincFile = miniZincFile;
		allSolutions = new LinkedList<>();
		MiniZincSolution solution;
		MiniZincSolution lastSolution = null;
		MiniBrassPostProcessor postProcessor = new MiniBrassPostProcessor();
		if (randomize) {
			randomSequence = new Random(initialRandomSeed);
		}
		miniZincRunner.getConfiguration().setUseAllSolutions(false); // in our configuration, we do not want to see all solutions

		// parse and compile MiniBrass
		miniBrassCompiler.setMinizincOnly(true);
		String compiledMiniBrassCode = miniBrassCompiler.compileInMemory(miniBrassFile);
		// for domination search
		String getBetterConstraint = miniBrassCompiler.getUnderlyingParser().getLastModel().getDereferencedSolveInstance().getGeneratedBetterPredicate();

		// search for solutions
		File workingMiniZincModel = appendMiniZincCode(originalMiniZincFile, compiledMiniBrassCode, true);
		while ((solution = findNextSolution(workingMiniZincModel, dataFiles)) != null) {
			// append solution
			allSolutions.add(solution);
			lastSolution = solution;

			// print solution in debug mode
			String updatedConstraint = "constraint " + postProcessor.processSolution(getBetterConstraint, solution) + ";";

			if (debug) {
				System.out.println("Found solution: ");
				System.out.println(solution.getRawDznSolution());

				// process getBetterConstraint with actual solution
				System.out.println("I got the following template constraint: ");
				System.out.println(getBetterConstraint);
				System.out.println(updatedConstraint);

			}

			// add constraint to model
			// TODO this is not the best way to do it - we should keep the String in main memory
			workingMiniZincModel = appendMiniZincCode(workingMiniZincModel, updatedConstraint);
			// solve again
		}
		cleanup(workingMiniZincModel);
		return lastSolution;
	}

	public static void main(String[] args) throws SecurityException, IOException {
		logger = Logger.getGlobal();
		logger.setLevel(Level.FINER);
		System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %2$s %5$s%6$s%n");

		SimpleFormatter formatterTxt = new SimpleFormatter();
		FileHandler logFile = new FileHandler("log2.txt");
		logFile.setLevel(Level.FINER);

		logFile.setFormatter(formatterTxt);
		logger.addHandler(logFile);

		// Create and set handler
		Handler systemOut = new StdoutConsoleHandler();
		systemOut.setLevel(Level.ALL);

		logger.addHandler(systemOut);
		logger.setLevel(Level.SEVERE);

		// Prevent logs from processed by default Console handler.
		logger.setUseParentHandlers(false); // Solution 1

		new MiniBrassRunner().doMain(args);
	}

	private void printUsage(Options options) {
		new HelpFormatter().printHelp(
				"minibrass [<options>] <minibrass-model>.mbr <minizinc-model>.mzn <minizinc-data>.dzn\n\nOptions:\n",
				options);
	}

	private void doMain(String[] args) {
		// create the command line parser
		CommandLineParser parser = new DefaultParser();

		Options options = new Options()
				.addOption("h", "help", false, "print this message")
				.addOption("d", "debug", false, "write intermediate files")
				.addOption("w", "weak-opt", false, "only use non-domination search")
				.addOption("r", "random-seed", true, "initial random seed for branch-and-bound")
				.addOption("s", "solver", true, "solver to use for branch-and-bound")
				.addOption("t", "timeout", true, "timeout in milliseconds");

		String minibrassFile = null;
		String minizincModelFile = null;
		String minizincDataFile = null;
		List<File> dataFiles = new LinkedList<>();

		try {
			// parse the command line arguments
			CommandLine line = parser.parse(options, args);

			List<String> argList = line.getArgList();

			if (line.hasOption('h')) {
				printUsage(options);
				System.exit(0);
			}

			if (line.hasOption("solver")) {
				miniZincRunner.getConfiguration().setSolverId(line.getOptionValue("solver"));
			}

			if (line.hasOption("timeout")) {
				miniZincRunner.getConfiguration().setTimeout(Integer.parseInt(line.getOptionValue("timeout")));
			}

			if (line.hasOption("random-seed")) {
				setInitialRandomSeed(Integer.parseInt(line.getOptionValue("random-seed")));
			}

			if (line.hasOption("weak-opt")) {
				dominationSearch = false;
			}

			if (argList.size() < 2) {
				System.out.println(
						"minibrass expects one MiniBrass file, one MiniZinc file and optionally some data files as input.");
				printUsage(options);
				System.exit(1);
			} else {
				minibrassFile = argList.get(0);
				if (!minibrassFile.endsWith("mbr")) {
					System.out.println("Warning: MiniBrass file ending on .mbr expected!");
				}

				minizincModelFile = argList.get(1);
				if (!minizincModelFile.endsWith("mzn")) {
					System.out.println("Warning: MiniZinc file ending on .mzn expected!");
				}

				if (argList.size() > 2) {
					minizincDataFile = argList.get(2);
					if (!minizincDataFile.endsWith("dzn")) {
						System.out.println("Warning: MiniZinc data file ending on .dzn expected!");
					}
					dataFiles.add(new File(minizincDataFile));
				}
			}

			if (line.hasOption("debug")) {
				debug = true;
				miniZincRunner.setDebug(true);
			}

			logger.info(
					"Processing " + minibrassFile + " | " + minizincModelFile + " | " + minizincDataFile + " to file.");
			executeBranchAndBound(new File(minizincModelFile), new File(minibrassFile), dataFiles);

		} catch (ParseException exp) {
			logger.severe("Unexpected exception:" + exp.getMessage());
			printUsage(options);
		} catch (FileNotFoundException e) {
			logger.severe("File was not found");
			e.printStackTrace();
		} catch (MiniBrassParseException e) {
			logger.severe("Could not parse MiniBrass model:");
			e.printStackTrace();
		} catch (IOException e) {
			logger.severe("IO error: ");
			e.printStackTrace();
		}

	}

	private File appendMiniZincCode(File miniZincFile, String updatedConstraint) throws IOException {
		return appendMiniZincCode(miniZincFile, updatedConstraint, false);
	}

	private File appendMiniZincCode(File miniZincFile, String updatedConstraint, boolean enforceIntermediateFile) throws IOException {
		if (writeIntermediateFiles || enforceIntermediateFile) {
			File nextFile = getNextMiniZincFile(miniZincFile);
			FileUtils.copyFile(miniZincFile, nextFile);
			cleanup(miniZincFile);
			miniZincFile = nextFile;
		}

		FileWriter fw = new FileWriter(miniZincFile, true);
		fw.write("\n");
		fw.write(updatedConstraint);
		fw.close();
		return miniZincFile;
	}

	private File getNextMiniZincFile(File miniZincFile) {
		String name = FilenameUtils.removeExtension(miniZincFile.getName());
		int modelIndex = 0;
		Matcher modelIndexMatcher = Pattern.compile(".*_([0-9]+)$").matcher(name);
		if (modelIndexMatcher.matches()) {
			String modelIndexText = modelIndexMatcher.group(1);
			name = name.substring(0, name.length() - modelIndexText.length() - 1);
			modelIndex = Integer.parseInt(modelIndexText) + 1;
		}
		String nextName = FilenameUtils.removeExtension(name) + "_" + modelIndex + ".mzn";
		return new File(miniZincFile.getParentFile(), nextName);
	}

	private void cleanup(File miniZincFile) {
		if (!miniZincFile.equals(originalMiniZincFile) && !debug)
			FileUtils.deleteQuietly(miniZincFile);
	}

	private MiniZincSolution findNextSolution(File miniZincFile, List<File> dataFiles) {
		if (randomize) {
			miniZincRunner.getConfiguration().setRandomSeed(randomSequence.nextInt(RANDOM_SEED_LIMIT));
		}
		MiniZincResult result = miniZincRunner.solve(miniZincFile, dataFiles, timeoutInSeconds);
		return !result.isInvalidated() && result.isSolved() ? result.getLastSolution() : null;
	}

	public MiniZincConfiguration getMiniZincRunnerConfiguration() {
		return miniZincRunner.getConfiguration();
	}

	public void setMiniZincConfiguration(MiniZincConfiguration configuration) {
		miniZincRunner.setConfiguration(configuration);
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public List<MiniZincSolution> getAllSolutions() {
		return allSolutions;
	}

	public void setAllSolutions(List<MiniZincSolution> allSolutions) {
		this.allSolutions = allSolutions;
	}

	public boolean isDominationSearch() {
		return dominationSearch;
	}

	public void setDominationSearch(boolean dominationSearch) {
		this.dominationSearch = dominationSearch;
	}

	public MiniZincRunner getMiniZincRunner() {
		return miniZincRunner;
	}

	public int getInitialRandomSeed() {
		return initialRandomSeed;
	}

	public void setInitialRandomSeed(int initialRandomSeed) {
		this.initialRandomSeed = initialRandomSeed;
		this.randomize = true;
	}

	public boolean writeIntermediateFiles() {
		return writeIntermediateFiles;
	}

	public void setWriteIntermediateFiles(boolean writeIntermediateFiles) {
		this.writeIntermediateFiles = writeIntermediateFiles;
	}

	public void setTimeoutInSeconds(Integer timeout) {
		this.timeoutInSeconds = timeout;
	}

}
