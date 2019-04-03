package babycino;

import java.io.*;
import java.lang.*;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;

enum COMPILE_MODE {
	BABYCINO,
	JAVA
}

class ProcessContext {
	public String in;
	public String out;

	public List<String> stream = new ArrayList<String>();
}

class StreamConsumer extends Thread
{
	InputStream stream;
	List<String> out;

	StreamConsumer(InputStream stream, List<String> out) {
		this.stream = stream;
		this.out = out;
	}

	public void run() {
		try {
			InputStreamReader reader = new InputStreamReader(stream);
			BufferedReader buffer = new BufferedReader(reader);
			String line;
			while ((line = buffer.readLine()) != null)
				if (out != null)
					out.add(line);
		} catch(Exception e) { }		
	}
}

class TimeoutException extends Exception
{
	public TimeoutException(String message) {
		super(message);
	}
}

// Initiates and manages the lifetime of a process
class ProcessRunner
{
	private final static Long TIMEOUT = 5L; // How long (in seconds) that a process can hang before it is killed

	ProcessBuilder builder = null;

	ProcessRunner(List<String> commands) {
		builder = new ProcessBuilder(commands);
		builder.redirectErrorStream(true); // Redirect error output to normal output stream for convenience
	}

	public ProcessContext exec() { // 
		ProcessContext context = new ProcessContext();
		try {
			Process process = builder.start();
			new StreamConsumer(process.getInputStream(), context.stream).run(); // Write all process output to the associated context object stream member
			process.waitFor(TIMEOUT, TimeUnit.SECONDS); // Yield for n seconds, or until process is gracefully destructed
			if (process.isAlive()) { // Check if the process is still active
				process.destroyForcibly();
				String cmds = "";
				for (String cmd : builder.command())
					cmds += cmd + " ";
				throw new TimeoutException("ProcessRunner timed out when executing command " + cmds);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return context;
	}
}

class CompilerWrapper
{
	private String pathToFileName(String path) {
		Path ppath = Paths.get(path);
		String fileName = ppath.getFileName().toString();
		fileName = fileName.substring(0, fileName.lastIndexOf("."));
		return fileName;
	}

	private ProcessContext babycino_compileToC(String in) {
		List<String> commands = new ArrayList();
		commands.add("java");
		commands.add("-jar");
		commands.add("target\\Babycino-0.3.jar");
		commands.add(in);
		String fileName = pathToFileName(in);
		commands.add("temp\\" + fileName + ".c");
		ProcessRunner runner = new ProcessRunner(commands);
		ProcessContext result = runner.exec();
		result.in = in;
		result.out = "temp\\" + fileName + ".c";
		return result;
	}

	private ProcessContext babycino_compileToExecutable(String in) {
		List<String> commands = new ArrayList();
		commands.add("gcc");
		commands.add(in);
		commands.add("-o");
		String fileName = pathToFileName(in);
		commands.add("temp\\" + fileName + ".out");
		ProcessRunner runner = new ProcessRunner(commands);
		ProcessContext result = runner.exec();
		result.in = in;
		result.out = "temp\\" + fileName + ".out";
		return result;
	}

	private int babycino_run(String in) {
		List<String> commands = new ArrayList();
		commands.add(in);
		ProcessRunner runner = new ProcessRunner(commands);
		ProcessContext result = runner.exec();
		int out = Integer.parseInt(result.stream.get(0));
		return out;
	}

	private ProcessContext java_compileToClass(String in) {
		List<String> commands = new ArrayList();
		commands.add("javac");
		commands.add(in);
		commands.add("-d");
		commands.add("temp");
		ProcessRunner runner = new ProcessRunner(commands);
		ProcessContext result = runner.exec();
		result.in = in;
		String fileName = pathToFileName(in);
		result.out = "temp\\" + fileName + ".class";
		return result;
	}

	private int java_run(String in) {
		List<String> commands = new ArrayList();
		commands.add("java");
		commands.add("-cp");
		commands.add("temp");
		in = in.replace("temp\\", "");
		in = in.replace(".class", "");
		commands.add(in);
		ProcessRunner runner = new ProcessRunner(commands);
		ProcessContext result = runner.exec();
		int out = Integer.parseInt(result.stream.get(0));
		return out;
	}

	public int compileAndRun(String in, COMPILE_MODE mode) {
		int result = 0;
		switch(mode) {
			case BABYCINO:
				ProcessContext bc_C = babycino_compileToC(in);
				ProcessContext bc_E = babycino_compileToExecutable(bc_C.out);
				result = babycino_run(bc_E.out);
				break;
			case JAVA:
				ProcessContext ja_C = java_compileToClass(in);
				result = java_run(ja_C.out);
				break;
		}
		return result;
	}
}