package org.codehaus.mojo.jspc;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang.time.StopWatch;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.codehaus.mojo.jspc.compiler.JspCompiler;
import org.codehaus.plexus.util.InterpolationFilterReader;
import org.codehaus.plexus.util.StringUtils;

abstract class CompilationMojoSupport extends AbstractMojo {
    /**
     * The working directory to create the generated java source files.
     *
     * @parameter expression="${project.build.directory}/jsp-source"
     * @required
     */
    String workingDirectory;
    
    /**
     * The sources of the webapp.  Default is <tt>${basedir}/src/main/webapp</tt>.
     *
     * @parameter
     */
    FileSet sources;
    
    /**
     * The path and location to the web fragment file.
     *
     * @parameter expression="${project.build.directory}/web-fragment.xml"
     * @required
     */
    File webFragmentFile;
    
    /**
     * The path and location of the original web.xml file.
     *
     * @parameter expression="${basedir}/src/main/webapp/WEB-INF/web.xml"
     * @required
     */
    File inputWebXml;
    
    /**
     * The final path and file name of the web.xml.
     *
     * @parameter expression="${project.build.directory}/jspweb.xml"
     * @required
     */
    File outputWebXml;
    
    /**
     * Character encoding.
     *
     * @parameter
     */
    String javaEncoding;

    //
    // TODO: Rename these, they are not descriptive enough
    //
    
    /**
     * Provide source compatibility with specified release.
     *
     * @parameter
     */
    String source;

    /**
     * Generate class files for specific VM version.
     *
     * @parameter
     */
    String target;

    /**
     * Sets if you want to compile the JSP classes.
     *
     * @parameter default-value="true"
     */
    boolean compile;

    /**
     * Set this to false if you don"t want to include the compiled JSPs
     * in your web.xml nor add the generated sources to your project"s
     * source roots.
     *
     * @parameter default-value="true"
     */
    boolean includeInProject;

    /**
     * The string to look for in the web.xml to replace with the web fragment
     * contents
     * 
     * If not defined, fragment will be appended before the &lt;/webapp&gt; tag
     * which is fine for servlet 2.4 and greater.  If using this parameter its
     * recommanded to use Strings such as
     * &lt;!-- [INSERT FRAGMENT HERE] --&gt;
     *
     * Be aware the &lt; and &gt; are for your pom verbatim.
     *
     * @parameter default-value="</web-app>"
     */
    String injectString;
    
    private static final String DEFAULT_INJECT_STRING = "</web-app>";
    
    /**
     * The package in which the jsp files will be contained.
     *
     * @parameter default-value="jsp"
     */
    String packageName;

    /**
     * Verbose level option for JspC.
     *
     * @parameter default-value="0"
     */
    int verbose;

    /**
     * Show Success option for JspC.
     *
     * @parameter default-value="true"
     */
    boolean showSuccess;

    /**
     * Set Smap Dumped option for JspC.
     *
     * @parameter default-value="false"
     */
    boolean smapDumped;

    /**
     * Set Smap Suppressed option for JspC.
     *
     * @parameter default-value="false"
     */
    boolean smapSuppressed;

    /**
     * List Errors option for JspC.
     *
     * @parameter default-value="true"
     */
    boolean listErrors;

    /**
     * Validate XML option for JspC.
     *
     * @parameter default-value="false"
     */
    boolean validateXml;

    /**
     * Removes the spaces from the generated JSP files.
     *
     * @parameter default-value="true"
     */
    boolean trimSpaces;

    /**
     * Provides filtering of the generated web.xml text.
     *
     * @parameter default-value="true"
     */
    boolean filtering;

    /**
     * Set to {@code true} to disable the plugin.
     *
     * @parameter expression="${jspc.skip}" default-value="false"
     */
    boolean skip;

    /**
     * Issue an error when the value of the class attribute in a useBean action is
     * not a valid bean class
     *
     * @parameter default-value="true"
     */
    boolean errorOnUseBeanInvalidClassAttribute;

    // Sub-class must provide
    protected abstract List getClasspathElements();
    
    //
    // Components
    //

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     */
    private JspCompiler jspCompiler;
    
    //
    // Mojo
    //

    @Override
    public void execute() {
        if (skip) {
            return;
        }
        
        final Log log = this.getLog();
        
        boolean isWar = "war" == project.getPackaging();

        if (!isWar || !includeInProject) {
            log.warn("Compiled JSPs will not be added to the project and web.xml will " +
                     "not be modified, either because includeInProject is set to false or " +
                     "because the project's packaging is not 'war'.");
        }

        // Setup defaults (complex, can"t init from expression)
        if (sources == null) {
            sources = new FileSet();
            sources.setDirectory("${project.basedir}/src/main/webapp");
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Source directory: $sources.directory");
            log.debug("Classpath: $classpathElements");
            log.debug("Output directory: $workingDirectory");
        }

        //
        // FIXME: Need to get rid of this and add a more generic way to configure the compiler
        //        perhaps nested configuration object for these details.  Only require the basics
        //        in mojo parameters that apply to all
        //
        
        List<String> args = new ArrayList<String>();
        
        args.add("-uriroot");
        args.add(sources.getDirectory());
        
        args.add("-d");
        args.add(workingDirectory);
        
        if (javaEncoding != null) {
            args.add("-javaEncoding");
            args.add(javaEncoding);
        }
        
        if (showSuccess) {
            args.add("-s");
        }
        
        if (listErrors) {
            args.add("-l");
        }
        
        args.add("-webinc");
        args.add(webFragmentFile.getAbsolutePath());
        
        args.add("-p");
        args.add(packageName);
        
        args.add("-classpath");
        args.add(classpathElements.join(File.pathSeparator));

        int count = 0;
        if (sources.getIncludes() != null) {
            final FileSetManager fsm = new FileSetManager();
            sources.setUseDefaultExcludes(true);
            final String[] includes = fsm.getIncludedFiles(sources);
            count = includes.length;
                
            for (final String it : includes) {
                args.add(new File(sources.getDirectory(), it).toString());
            }
        }
        
        jspCompiler.setArgs(args.toArray(new String[0]));
        log.debug("Jspc args: " + args);
        
        jspCompiler.smapDumped = smapDumped
        jspCompiler.smapSuppressed = smapSuppressed
        jspCompiler.compile = compile
        jspCompiler.validateXml = validateXml
        jspCompiler.trimSpaces = trimSpaces
        jspCompiler.verbose = verbose
        jspCompiler.errorOnUseBeanInvalidClassAttribute = errorOnUseBeanInvalidClassAttribute
        jspCompiler.compilerSourceVM = source
        jspCompiler.compilerTargetVM = target
        
        // Make directories if needed
        ant.mkdir(dir: workingDirectory)
        ant.mkdir(dir: project.build.directory)
        ant.mkdir(dir: project.build.outputDirectory)
        
        // JspC needs URLClassLoader, with tools.jar
        def parent = Thread.currentThread().contextClassLoader
        def cl = new JspcMojoClassLoader(parent)
        cl.addURL(findToolsJar().toURI().toURL())
        Thread.currentThread().setContextClassLoader(cl)

        try {
            // Show a nice message when we know how many files are included
            if (count) {
                log.info("Compiling $count JSP source file" + (count > 1 ? "s" : "") + " to $workingDirectory")
            }
            else {
                log.info("Compiling JSP source files to $workingDirectory")
            }
            
            def watch = new StopWatch()
            watch.start()
            
            jspCompiler.compile()
            
            log.info("Compilation completed in $watch")
        }
        finally {
            // Set back the old classloader
            Thread.currentThread().contextClassLoader = parent
        }
        
        // Maybe install the generated classes into the default output directory
        if (compile && isWar) {
            ant.copy(todir: project.build.outputDirectory) {
                fileset(dir: workingDirectory) {
                    include(name: "**/*.class")
                }
            }
        }
        
        if (isWar && includeInProject) {
            writeWebXml()
            project.addCompileSourceRoot(workingDirectory)
        }
    }
    
    /**
     * Figure out where the tools.jar file lives.
     */
    private File findToolsJar() {
        def javaHome = new File(System.properties["java.home"])
        
        def file
        if (SystemUtils.IS_OS_MAC_OSX) {
            file = new File(javaHome, "../Classes/classes.jar").canonicalFile
        }
        else {
            file = new File(javaHome, "../lib/tools.jar").canonicalFile
        }
        
        assert file.exists() : "Missing tools.jar at: $file"
        
        log.debug("Using tools.jar: $file")
        
        return file
    }
    
    private void writeWebXml() {
        // Read the files
        assert inputWebXml.exists()
        String webXml = inputWebXml.text

        def m = java.util.regex.Pattern
            .compile("<\\?xml .*encoding\\s*=\\s*["\"]([^"\"]+)["\"].*\\?>")
            .matcher(webXml);
        String encoding = null;
        if (m.find()){
            encoding = m.group(1);
        }
        if(encoding == null){
            encoding = "UTF-8";
        }
        if(!encoding.equalsIgnoreCase(System.getProperty("file.encoding"))){
            webXml = inputWebXml.getText(encoding);
        }
        
        assert webFragmentFile.exists()
        String fragmentXml = webFragmentFile.text
        
        if (webXml.indexOf(injectString) == -1) {
            fail("Missing inject string: "$injectString" in: $inputWebXml")
        }
        
        def output = StringUtils.replace(webXml, injectString, fragmentXml)
        
        // If using the default, then tack on the end of the document
        if (injectString == DEFAULT_INJECT_STRING) {
            output += DEFAULT_INJECT_STRING
        }
        
        // Allow generated xml to be filtered
        if (filtering) {
            output = filter(output)
        }

        // Write the file
        outputWebXml.parentFile.mkdirs()
        outputWebXml.write(output, encoding)
    }

    private String filter(String input) {
        assert input
        
        def reader = new StringReader(input)
        
        // Setup chained readers to filter
        reader = new InterpolationFilterReader(reader, project.properties, "${", "}")
        reader = new InterpolationFilterReader(reader, project.properties, "@", "@")
        
        return reader.readLines().join("\n")
    }
}
