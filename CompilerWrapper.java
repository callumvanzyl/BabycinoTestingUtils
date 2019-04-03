// By 26006007
// For compilers coursework @ The University of Reading

package babycino;

import java.io.*;
import java.lang.*;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;

// Defines what toolset will be used when compiling a .java file
enum COMPILE_MODE {
    BABYCINO,
    BABYCINO_UNOPTIMISED,
    JAVAC
}

// Stores information about a process' lifetime
class ProcessContext {
    public String in; // The path to the input file
    public String out; // The path to the output file

    public List<String> stream = new ArrayList<String>(); // Copy of the process' input stream
	
	public int result = -1; // The result produced by the process, if any

    public void combine(List<String> other) {
        stream.addAll(other);
    }
}

// Used to output a process' input stream to a list
class StreamConsumer extends Thread
{
    InputStream stream; // The stream to copy
    List<String> out; // Where to output the stream's contents to

    StreamConsumer(InputStream stream, List<String> out) {
        this.stream = stream;
        this.out = out;
    }

    public void run() {
        try {
            InputStreamReader reader = new InputStreamReader(stream);
            BufferedReader buffer = new BufferedReader(reader);
            String line;
            while ((line = buffer.readLine()) != null) // Read the next line in the input stream
                if (out != null) // An output may not have been defined
                    out.add(line);
        } catch(Exception e) { }        
    }
}

// This exception will be thrown in cases where a process hangs for too long
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
        ProcessContext context = new ProcessContext(); // Create an empty processor context
        try {
            Process process = builder.start();
            new StreamConsumer(process.getInputStream(), context.stream).run(); // Write all process output to the associated context object stream member
            process.waitFor(TIMEOUT, TimeUnit.SECONDS); // Yield for n seconds, or until process is gracefully destructed
            if (process.isAlive()) { // Check if the process is still active
                process.destroyForcibly(); // Kill the process
                String cmds = ""; // Write an error message and throw an exception
                for (String cmd : builder.command())
                    cmds += cmd + " ";
                throw new TimeoutException("ProcessRunner timed out when executing command " + cmds);
            }
        } catch(Exception e) { }
        return context;
    }
}

// TODO: A lot of this was rushed so it looks ugly. Generalise behavior, make it look nicer. We don't need a seperate workflow for unoptimised Babycino.
class CompilerWrapper
{
	// Extracts a filename from a path (Ex: temp/abc.java would be converted to just abc)
    private String pathToFileName(String path) {
        Path ppath = Paths.get(path);
        String fileName = ppath.getFileName().toString();
        fileName = fileName.substring(0, fileName.lastIndexOf("."));
        return fileName;
    }

	// Compile to C using Babycino
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

    // Compile to C using Babycino (unoptimised)
    private ProcessContext babycinoUnoptimised_compileToC(String in) {
        List<String> commands = new ArrayList();
        commands.add("java");
        commands.add("-jar");
        commands.add("target\\Babycino-0.3.jar");
        commands.add(in);
        String fileName = pathToFileName(in);
        commands.add("temp\\" + fileName + ".c");
        commands.add("-unoptimised");
        ProcessRunner runner = new ProcessRunner(commands);
        ProcessContext result = runner.exec();
        result.in = in;
        result.out = "temp\\" + fileName + ".c";
        return result;
    }

	// Compile C code to an executable using gcc
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

	// Run an executable
    private ProcessContext babycino_run(String in) {
        List<String> commands = new ArrayList();
        commands.add(in);
        ProcessRunner runner = new ProcessRunner(commands);
        ProcessContext result = runner.exec();
		return result;
    }

	// Compile using javac
    private ProcessContext javac_compileToClass(String in) {
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

	// Run a class using Java
    private ProcessContext javac_run(String in) {
        List<String> commands = new ArrayList();
        commands.add("java");
        commands.add("-cp");
        commands.add("temp");
        in = in.replace("temp\\", "");
        in = in.replace(".class", "");
        commands.add(in);
        ProcessRunner runner = new ProcessRunner(commands);
        ProcessContext result = runner.exec();
        return result;
    }

	public ProcessContext compileAndRun(String in, COMPILE_MODE mode) {
        ProcessContext all = new ProcessContext();
        all.in = in;
        switch(mode) {
            case BABYCINO: 
                ProcessContext bc_C = babycino_compileToC(in); //.java->.c
                all.combine(bc_C.stream);
                ProcessContext bc_E = babycino_compileToExecutable(bc_C.out); //.c->.out(executable)
                all.combine(bc_E.stream);
                ProcessContext bc_R = babycino_run(bc_E.out); //run it
                all.combine(bc_R.stream);
                all.out = bc_R.out;
                if (bc_R.stream.size() > 0)
			        all.result = Integer.parseInt(bc_R.stream.get(0));
                break;
            case BABYCINO_UNOPTIMISED: 
                ProcessContext bcu_C = babycinoUnoptimised_compileToC(in); //.java->.c
                all.combine(bcu_C.stream);
                ProcessContext bcu_E = babycino_compileToExecutable(bcu_C.out); //.c->.out(executable)
                all.combine(bcu_E.stream);
                ProcessContext bcu_R = babycino_run(bcu_E.out); //run it
                all.combine(bcu_R.stream);
                all.out = bcu_R.out;
                if (bcu_R.stream.size() > 0)
			        all.result = Integer.parseInt(bcu_R.stream.get(0));
                break;
            case JAVAC:
                ProcessContext jc_C = javac_compileToClass(in); //.java->.class(executable)
                all.combine(jc_C.stream);
                ProcessContext jc_R = javac_run(jc_C.out); //run it
                all.combine(jc_R.stream);
                all.out = jc_R.out;
                if (jc_R.stream.size() > 0)
			        all.result = Integer.parseInt(jc_R.stream.get(0));
                break;
        }
        return all;
    }
}