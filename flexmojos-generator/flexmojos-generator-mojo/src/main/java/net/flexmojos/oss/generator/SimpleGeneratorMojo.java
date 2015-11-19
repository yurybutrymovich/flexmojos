/**
 * Flexmojos is a set of maven goals to allow maven users to compile, optimize and test Flex SWF, Flex SWC, Air SWF and Air SWC.
 * Copyright (C) 2008-2012  Marvin Froeder <marvin@flexmojos.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.flexmojos.oss.generator;

import static java.lang.Thread.currentThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.SelectorUtils;
import net.flexmojos.oss.generator.GenerationException;
import net.flexmojos.oss.generator.GenerationRequest;
import net.flexmojos.oss.generator.Generator;
import net.flexmojos.oss.generator.GeneratorFactory;

/**
 * This goal generate code based on Java classes.
 * 
 * @author Marvin Herman Froeder (velo.br@gmail.com)
 * @author edward.yakop@gmail.com
 * @goal generate
 * @phase generate-sources
 * @requiresDependencyResolution test
 * @since 3.6
 */
public class SimpleGeneratorMojo
    extends AbstractMojo
{

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * File to generate as3 file. If not defined assumes all classes must be included
     * 
     * @parameter
     */
    private String[] includeJavaClasses;

    /**
     * File to exclude from as3 generation. If not defined, assumes no exclusions
     * 
     * @parameter
     */
    private String[] excludeJavaClasses;

    /**
     * @parameter default-value="${project.build.sourceDirectory}"
     */
    private File outputDirectory;

    /**
     * @parameter default-value="${project.build.directory}/generated-sources/flexmojos"
     */
    private File baseOutputDirectory;

    /**
     * @parameter default-value="graniteds310" expression="${generatorToUse}"
     */
    private String generatorToUse;

    /**
     * @component role="net.flexmojos.oss.generator.GeneratorFactory"
     */
    private GeneratorFactory generatorFactory;

    /**
     * Configurations used by the generator implementation, check generator docs to see which parameters can be used.
     * 
     * @parameter
     */
    private Map<String, String> extraOptions;

    /**
     * Templates used by the generator implementation, check generator docs to see which Templates can be used. Example:
     * 
     * <pre>
     * &lt;templates&gt;
     *   &lt;base-enum-template&gt;your-template&lt;/base-enum-template&gt;
     * &lt;/templates&gt;
     * </pre>
     * 
     * @parameter
     */
    private Map<String, String> templates;

    /**
     * A '=' separated list of Strings, format: packageToTranslate=packageToReplace
     * 
     * @parameter
     */
    private String[] translators;

    public void execute()
        throws MojoExecutionException
    {
        setUp();

        GeneratorLogger logger = new MavenGeneratorLogger( getLog() );

        GenerationRequest request = new GenerationRequest();
        request.setClasses( getFilesToGenerator() );
        request.setClassLoader( this.initializeClassLoader() );
        request.setExtraOptions( extraOptions );
        request.setPersistentOutputFolder( outputDirectory );
        request.setTemplates( templates );
        request.setTransientOutputFolder( baseOutputDirectory );
        request.setTranslators( translators );
        request.setLogger( logger );

        ClassLoader cl = currentThread().getContextClassLoader();

        try
        {
            currentThread().setContextClassLoader( request.getClassLoader() );

            Generator generator = generatorFactory.getGenerator( generatorToUse );
            generator.generate( request );
        }
        catch ( GenerationException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        finally
        {
            currentThread().setContextClassLoader( cl );
        }
    }

    private void setUp()
        throws MojoExecutionException
    {
        if ( includeJavaClasses == null )
        {
            includeJavaClasses = new String[] { "*" };
        }

        if ( !outputDirectory.exists() )
        {
            outputDirectory.mkdirs();
        }

        String outputPath = outputDirectory.getAbsolutePath();
        if ( !project.getCompileSourceRoots().contains( outputPath ) )
        {
            project.addCompileSourceRoot( outputPath );
        }

        if ( !baseOutputDirectory.exists() )
        {
            baseOutputDirectory.mkdirs();
        }
        String baseOutputPath = baseOutputDirectory.getAbsolutePath();
        if ( !project.getCompileSourceRoots().contains( baseOutputPath ) )
        {
            project.addCompileSourceRoot( baseOutputPath );
        }

        if ( translators == null )
        {
            translators = new String[0];
        }
    }

    private final Map<String, File> getFilesToGenerator()
        throws MojoExecutionException
    {
        List<String> classpaths = getDirectDependencies();
        Map<String, File> classes = new HashMap<String, File>();

        for ( String fileName : classpaths )
        {
            File file = new File( fileName ).getAbsoluteFile();

            if ( file.isDirectory() )
            {
                DirectoryScanner ds = new DirectoryScanner();
                ds.setBasedir( file );
                ds.setIncludes( new String[] { "**/*.class" } );
                ds.scan();

                for ( String classFileName : ds.getIncludedFiles() )
                {
                    String className = classFileName.replace( File.separatorChar, '.' );
                    className = className.substring( 0, className.length() - 6 );

                    if ( matchWildCard( className, includeJavaClasses )
                        && !matchWildCard( className, excludeJavaClasses ) )
                    {
                        classes.put( className, new File( file, classFileName ) );
                    }
                }
            }
            else
            {

                try
                {
                    JarInputStream jar = new JarInputStream( new FileInputStream( file ) );

                    JarEntry jarEntry;
                    while ( true )
                    {
                        jarEntry = jar.getNextJarEntry();

                        if ( jarEntry == null )
                        {
                            break;
                        }

                        String className = jarEntry.getName();

                        if ( jarEntry.isDirectory() || !className.endsWith( ".class" ) )
                        {
                            continue;
                        }

                        className = className.replace( '/', '.' );
                        className = className.substring( 0, className.length() - 6 );

                        if ( matchWildCard( className, includeJavaClasses )
                            && !matchWildCard( className, excludeJavaClasses ) )
                        {
                            classes.put( className, file );
                        }
                    }
                }
                catch ( IOException e )
                {
                    throw new MojoExecutionException( "Error on classes resolve", e );
                }
            }
        }

        return classes;
    }

    private boolean matchWildCard( String className, String... wildCards )
    {
        if ( wildCards == null )
        {
            return false;
        }

        for ( String wildCard : wildCards )
        {
            if ( className.equals( wildCard ) )
            {
                return true;
            }

            if ( SelectorUtils.matchPath( wildCard, className ) )
            {
                return true;
            }
        }

        return false;
    }

    private ClassLoader initializeClassLoader()
        throws MojoExecutionException
    {
        List<String> classpaths = getClasspath();

        try
        {
            List<URL> classpathsUrls = new ArrayList<URL>();

            // add all the jars to the new child realm
            for ( String path : classpaths )
            {
                URL url = new File( path ).toURI().toURL();
                classpathsUrls.add( url );
            }

            return new URLClassLoader( classpathsUrls.toArray( new URL[0] ), currentThread().getContextClassLoader() );
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Unable to get dependency URL", e );
        }
    }

    private List<String> getClasspath()
        throws MojoExecutionException
    {
        List<String> classpaths;
        try
        {
            classpaths = project.getCompileClasspathElements();
            classpaths.remove( project.getBuild().getOutputDirectory() );
        }
        catch ( DependencyResolutionRequiredException e )
        {
            throw new MojoExecutionException( "Failed to find dependencies", e );
        }
        return classpaths;
    }

    private List<String> getDirectDependencies()
        throws MojoExecutionException
    {
        List<String> classpaths = new ArrayList<String>();
        Set<Artifact> artifacts = project.getDependencyArtifacts();
        for ( Artifact artifact : artifacts )
        {
            if ( "jar".equals( artifact.getType() ) || "maven-plugin".equals( artifact.getType() ) )
            {
                classpaths.add( artifact.getFile().getAbsolutePath() );
            }
        }
        return classpaths;
    }

}
