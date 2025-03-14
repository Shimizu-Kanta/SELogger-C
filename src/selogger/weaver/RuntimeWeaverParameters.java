package selogger.weaver;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import selogger.logging.io.FilterLogger.PartialSaveStrategy;
import selogger.logging.io.LatestEventLogger.ObjectRecordingStrategy;
import selogger.logging.io.ProposedmethodLogger.PrometObjectRecordingStrategy;
import selogger.logging.util.ObjectIdFile.ExceptionRecording;
import selogger.weaver.RuntimeWeaver.Mode;

/**
 * Parameters for controlling the behavior of a runtime weaver.
 */
public class RuntimeWeaverParameters {

	private static final String DEFAULT_TRACE_FILENAME = "trace";
	private static final String DEFAULT_WEAVERLOG_FILENAME = "weaverlog.txt";

	private static final String[] SYSTEM_PACKAGES = { "sun/", "com/sun/", "java/", "javax/", "javafx/", "jdk/" };
	private static final String ARG_SEPARATOR = ",";

	private static Pattern timePattern = Pattern.compile(".*(\\{time:([^}]+)\\}).*");

	private String output_dirname = null;

	private String weaveOption = WeaveConfig.KEY_RECORD_ALL;

	private String traceFileName = null;

	private String weaverLogFileName = null;

	private boolean outputJson = true;

	/**
	 * Dump woven class files (mainly for debugging)
	 */
	private boolean dumpClass = false;

	/**
	 * The number of events recorded per code location
	 */
	private int bufferSize = 32;

	/**
	 * Strategy to keep objects on memory
	 */
	private ObjectRecordingStrategy keepObject = ObjectRecordingStrategy.Strong;

	private PrometObjectRecordingStrategy PrometKeepObject = PrometObjectRecordingStrategy.Strong;

	/**
	 * If true, automatic filtering for security manager classes is disabled
	 */
	private boolean weaveSecurityManagerClass = false;

	/**
	 * If false, String content is discarded.
	 */
	private boolean recordString = true;

	/**
	 * Strategy to record exceptions
	 */
	private ExceptionRecording recordExceptions = ExceptionRecording.MessageAndStackTrace;

	/**
	 * Package/class names (prefix) excluded from logging
	 */
	private ArrayList<String> excludedNames;

	/**
	 * Exceptional package/class names (prefix) included in logging, ignoring
	 * excludedNames
	 */
	private ArrayList<String> includedNames;

	/**
	 * Location names (substring) excluded from logging
	 */
	private ArrayList<String> excludedLocations;

	private HashMap<String, DataInfoPattern> dataIdPatterns;

	/**
	 * Allow nested intervals for logstart and logend
	 */
	private boolean allowNestedIntervals = false;

	/**
	 * Specify whether a partial trace is stored or not
	 */
	private PartialSaveStrategy partialSave = PartialSaveStrategy.No;

	private Mode mode = Mode.FixedSize;

	/**
	 * A shared instance to generate the same datetime for files
	 */
	private Date currentDate = new Date();

	/**
	 * 追加要素：trim_amount
	 * premetモードでのトリム量を設定する
	 * デフォルトは16
	 */
	private int trimSize = 16;

	/**
	 * 追加要素：show_bufferSize
	 * 最終的なバッファサイズを見せるかどうかを決定する
	 * デフォルトはfalse
	 * @param args
	 */
	private boolean show_bufferSize = false;

	public RuntimeWeaverParameters(String args) {
		if (args == null)
			args = "";
		String[] a = args.split(ARG_SEPARATOR);
		includedNames = new ArrayList<String>();
		excludedNames = new ArrayList<String>();
		excludedLocations = new ArrayList<String>();
		dataIdPatterns = new HashMap<>();
		for (String pkg : SYSTEM_PACKAGES)
			excludedNames.add(pkg);

		for (String arg : a) {
			if (arg.startsWith("output=")) {
				output_dirname = fillTimePattern(arg.substring("output=".length()));
			} else if (arg.startsWith("trace=")) {
				traceFileName = fillTimePattern(arg.substring("trace=".length()));
			} else if (arg.startsWith("weaverlog=")) {
				weaverLogFileName = fillTimePattern(arg.substring("weaverlog=".length()));
			} else if (arg.startsWith("weave=")) {
				weaveOption = arg.substring("weave=".length());
			} else if (arg.startsWith("dump=")) {
				String classDumpOption = arg.substring("dump=".length());
				dumpClass = classDumpOption.equalsIgnoreCase("true");
			} else if (arg.startsWith("size=")) {
				bufferSize = Integer.parseInt(arg.substring("size=".length()));
				if (bufferSize < 4)
					bufferSize = 4;
			} else if (arg.startsWith("weavesecuritymanager=")) {
				weaveSecurityManagerClass = Boolean.parseBoolean(arg.substring("weavesecuritymanager=".length()));
			} else if (arg.startsWith("json=")) {
				String param = arg.substring("json=".length());
				outputJson = param.equalsIgnoreCase("true");
			} else if (arg.startsWith("keepobj=")) {
				String param = arg.substring("keepobj=".length());
				if (param.equalsIgnoreCase("true") || param.equalsIgnoreCase("strong")) {
					keepObject = ObjectRecordingStrategy.Strong;
					PrometKeepObject = PrometObjectRecordingStrategy.Strong;
				} else if (param.equalsIgnoreCase("false") || param.equalsIgnoreCase("weak")) {
					keepObject = ObjectRecordingStrategy.Weak;
					PrometKeepObject = PrometObjectRecordingStrategy.Weak;
				} else if (param.equalsIgnoreCase("id")) {
					keepObject = ObjectRecordingStrategy.Id;
					PrometKeepObject = PrometObjectRecordingStrategy.Id;
				}
			} else if (arg.startsWith("logstart=")) {
				DataInfoPattern p = new DataInfoPattern(arg.substring("logstart=".length()));
				if (p != null)
					dataIdPatterns.put("logstart", p);
			} else if (arg.startsWith("logend=")) {
				DataInfoPattern p = new DataInfoPattern(arg.substring("logend=".length()));
				if (p != null)
					dataIdPatterns.put("logend", p);
			} else if (arg.startsWith("lognested=")) {
				String option = arg.substring("lognested=".length());
				allowNestedIntervals = option.equalsIgnoreCase("true");
			} else if (arg.startsWith("logsave=")) {
				String option = arg.substring("logsave=".length());
				partialSave = PartialSaveStrategy.No;
				if (option.equalsIgnoreCase("partial")) {
					partialSave = PartialSaveStrategy.WriteAndReset;
				} else if (option.equalsIgnoreCase("snapshot")) {
					partialSave = PartialSaveStrategy.WriteSnapshot;
				}
			} else if (arg.startsWith("watch=")) {
				DataInfoPattern p = new DataInfoPattern(arg.substring("watch=".length()));
				if (p != null)
					dataIdPatterns.put("watch", p);
			} else if (arg.startsWith("string=")) {
				String param = arg.substring("string=".length());
				recordString = Boolean.parseBoolean(param);
			} else if (arg.startsWith("exception=")) {
				String param = arg.substring("exception=".length());
				if (param.equalsIgnoreCase("message")) {
					recordExceptions = ExceptionRecording.Message;
				} else if (param.equalsIgnoreCase("false") || param.equalsIgnoreCase("none")) {
					recordExceptions = ExceptionRecording.Disabled;
				} else {
					recordExceptions = ExceptionRecording.MessageAndStackTrace;
				}
			} else if (arg.startsWith("e=")) {
				String prefix = arg.substring("e=".length());
				if (prefix.length() > 0) {
					prefix = prefix.replace('.', '/');
					excludedNames.add(prefix);
				}
			} else if (arg.startsWith("i=")) {
				String prefix = arg.substring("i=".length());
				if (prefix.length() > 0) {
					prefix = prefix.replace('.', '/');
					includedNames.add(prefix);
				}
			} else if (arg.startsWith("exlocation=")) {
				String location = arg.substring("exlocation=".length());
				if (location.length() > 0) {
					excludedLocations.add(location);
				}
			} else if (arg.startsWith("format=")) {
				String opt = arg.substring("format=".length()).toLowerCase();
				if (opt.equals("freq")) {
					mode = Mode.Frequency;
				} else if (opt.equals("before")) {
					mode = Mode.ExecuteBefore;
				} else if (opt.equals("discard")) {
					mode = Mode.Discard;
				} else if (opt.equals("omni") || opt.equals("omnitext")) {
					mode = Mode.TextStream;
				} else if (opt.equals("stream") || opt.equals("omnibinary")) {
					mode = Mode.BinaryStream;
				} else if (opt.equals("latest") || opt.equals("nearomni") || opt.equals("near-omni")) {
					mode = Mode.FixedSize;
				} else if (opt.equals("promet") || opt.equals("Promet")){
					mode = Mode.Promet;
				} else {
					mode = Mode.Invalid;
				}
			} else if (arg.startsWith("trim=")) {
				trimSize = Integer.parseInt(arg.substring("trim=".length()));
				if (trimSize < 1) 
					trimSize = 1;
			} else if (arg.startsWith("showbuffersize=")) {
				String param = arg.substring("showbuffersize=".length());
				show_bufferSize = Boolean.parseBoolean(param);
			}
		}
	}

	/**
	 * @return a directory name. This method returns null if no directory is
	 *         specified
	 */
	public String getOutputDirname() {
		return output_dirname;
	}

	public File getTraceFile() {
		if (traceFileName == null) {
			String filename = DEFAULT_TRACE_FILENAME + (outputJson ? ".json" : ".txt");
			if (getOutputDirname() != null) {
				return new File(getOutputDir(), filename);
			} else {
				return new File(filename);
			}
		} else {
			return new File(traceFileName);
		}
	}
	
	public File getOutputDir() {
		File outputDir = null;
		if (output_dirname != null) {
			outputDir = new File(output_dirname); 
			if (!outputDir.exists()) {
				outputDir.mkdirs();
			}
		}
		return outputDir;
	}

	public File getWeaverLogFile() {
		if (weaverLogFileName == null) {
			if (getOutputDirname() != null) {
				return new File(getOutputDir(), DEFAULT_WEAVERLOG_FILENAME);
			} else {
				// Weaver log is not created by default
				return null;
			}
		} else {
			return new File(weaverLogFileName);
		}
	}

	public String getWeaveOption() {
		return weaveOption;
	}

	public boolean isDumpClassEnabled() {
		return dumpClass;
	}

	public Mode getMode() {
		return mode;
	}

	public int getBufferSize() {
		return bufferSize;
	}

	public ObjectRecordingStrategy getObjectRecordingStrategy() {
		return keepObject;
	}

	public PrometObjectRecordingStrategy getPrometObjectRecordingStrategy() {
		return PrometKeepObject;
	}

	public boolean isOutputJsonEnabled() {
		return outputJson;
	}

	public ExceptionRecording isRecordingExceptions() {
		return recordExceptions;
	}

	public boolean isRecordingString() {
		return recordString;
	}

	public boolean isWeaveSecurityManagerClassEnabled() {
		return weaveSecurityManagerClass;
	}

	public Map<String, DataInfoPattern> getLoggingTargetOptions() {
		return dataIdPatterns;
	}

	public boolean isNestedIntervalsAllowed() {
		return allowNestedIntervals;
	}

	public PartialSaveStrategy getPartialSaveStrategy() {
		return partialSave;
	}

	public int getTrimSize(){
		return trimSize;
	}

	public boolean getShowBufferSize(){
		return show_bufferSize;
	}

	/**
	 * This method checks whether a given class is a logging target or not.
	 * 
	 * @param className specifies a class. A package separator is "/".
	 * @return true if it is excluded from logging.
	 */
	public boolean isExcludedFromLogging(String className) {
		if (className.startsWith("selogger/") && !className.startsWith("selogger/testdata/"))
			return true;
		boolean excluded = false;
		for (String ex : excludedNames) {
			if (className.startsWith(ex)) {
				excluded = true;
				break;
			}
		}
		for (String prefix : includedNames) {
			if (className.startsWith(prefix)) {
				excluded = false;
				break;
			}
		}
		return excluded;
	}

	/**
	 * This method checks whether a given class is a logging target or not.
	 * 
	 * @param location is a loaded location (e.g. JAR or file path).
	 * @return true if it is excluded from logging.
	 */
	public boolean isExcludedLocation(String location) {
		for (String ex : excludedLocations) {
			if (location.contains(ex)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Fill time in the {time} and {time:format} patterns
	 * 
	 * @param s
	 * @return
	 */
	public String fillTimePattern(String s) {
		if (s.contains("{time}")) {
			SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmmssSSS");
			s = s.replace("{time}", f.format(currentDate));
		} else {
			Matcher m = timePattern.matcher(s);
			if (m.matches()) {
				SimpleDateFormat f = new SimpleDateFormat(m.group(2));
				s = s.replace(m.group(1), f.format(currentDate));
			}
		}
		return s;
	}

}
