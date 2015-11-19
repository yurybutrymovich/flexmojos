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
package net.flexmojos.oss.plugin.compiler;

import net.flexmojos.oss.compiler.ICommandLineConfiguration;
import net.flexmojos.oss.compiler.MxmlcConfigurationHolder;
import net.flexmojos.oss.compiler.command.Result;
import net.flexmojos.oss.plugin.compiler.attributes.Module;
import net.flexmojos.oss.plugin.utilities.MavenUtils;
import net.flexmojos.oss.plugin.utilities.SourceFileResolver;
import net.flexmojos.oss.truster.FlashPlayerTruster;
import net.flexmojos.oss.util.PathUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.flexmojos.oss.matcher.artifact.ArtifactMatcher.scope;
import static net.flexmojos.oss.matcher.artifact.ArtifactMatcher.type;
import static net.flexmojos.oss.plugin.common.FlexExtension.SWC;
import static net.flexmojos.oss.plugin.common.FlexExtension.SWF;
import static net.flexmojos.oss.plugin.common.FlexScopes.*;
import static net.flexmojos.oss.util.PathUtil.file;
import static org.hamcrest.Matchers.*;

/**
 * <p>
 * Goal which compiles the Flex sources into an application for either Flex or AIR depending on the package type.
 * </p>
 * <p>
 * The Flex Compiler plugin compiles all ActionScript sources. It can compile the source into 'swf' files. The plugin
 * supports 'swf' packaging.
 * </p>
 * 
 * @author Marvin Herman Froeder (velo.br@gmail.com)
 * @since 1.0
 * @goal compile-swf
 * @requiresDependencyResolution compile
 * @phase compile
 * @threadSafe
 */
public class MxmlcMojo
    extends AbstractFlexCompilerMojo<MxmlcConfigurationHolder, MxmlcMojo>
    implements ICommandLineConfiguration, Mojo
{

    /**
     * DOCME Again, undocumented by adobe
     * <p>
     * Equivalent to -file-specs
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;fileSpecs&gt;
     *   &lt;fileSpec&gt;???&lt;/fileSpec&gt;
     *   &lt;fileSpec&gt;???&lt;/fileSpec&gt;
     * &lt;/fileSpecs&gt;
     * </pre>
     * 
     * @parameter
     */
    private List<String> fileSpecs;

    /**
     * The list of modules to be compiled.
     * 
     * <pre>
     * &lt;modules&gt;
     *   &lt;module&gt;Module1.mxml&lt;/module&gt;
     *   &lt;module&gt;Module2.mxml&lt;/module&gt;
     *   &lt;module&gt;
     *     &lt;sourceFile&gt;Module3.mxml&lt;/sourceFile&gt;
     *     &lt;optimize&gt;false&lt;/optimize&gt;
     *     &lt;finalName&gt;MyModule&lt;/finalName&gt;
     *     &lt;destinationPath&gt;dir1/dir2&lt;/destinationPath&gt;
     *   &lt;/module&gt;
     * &lt;/modules&gt;
     * </pre>
     * 
     * @parameter
     */
    private Module[] modules;

    /**
     * When true, tells flexmojos to optimized modules using link reports/load externs
     * 
     * @parameter expression="${flex.modulesLoadExterns}" default-value="true"
     */
    private boolean modulesLoadExterns;

    /**
     * DOCME Another, undocumented by adobe
     * <p>
     * Equivalent to -projector
     * </p>
     * 
     * @parameter expression="${flex.projector}"
     */
    private String projector;

    /**
     * The file to be compiled. The path must be relative with source folder
     * 
     * @parameter expression="${flex.sourceFile}"
     */
    private String sourceFile;

    /**
     * @component
     * @required
     * @readonly
     */
    private FlashPlayerTruster truster;

    /**
     * When true, flexmojos will register register every compiled SWF files as trusted. These SWF files are assigned to
     * the local-trusted sandbox. They can interact with any other SWF files, and they can load data from anywhere,
     * remote or local. On false nothing is done, so if the file is already trusted it will still as it is.
     * 
     * @parameter default-value="true" expression="${updateSecuritySandbox}"
     */
    private boolean updateSecuritySandbox;

    public final Result doCompile( MxmlcConfigurationHolder cfg, boolean synchronize )
        throws Exception
    {
        if ( isUpdateSecuritySandbox() )
        {
            truster.updateSecuritySandbox( PathUtil.file( cfg.getConfiguration().getOutput() ) );
        }
        return compiler.compileSwf( cfg, synchronize, compilerName );
    }

    public void fmExecute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !PathUtil.existAny( getSourcePath() ) )
        {
            getLog().info( "Skipping compiler, source path doesn't exist. " + Arrays.toString( getSourcePath() ) );
            return;
        }

        executeCompiler( new MxmlcConfigurationHolder( this, getSourceFile() ), true );
        File output = file( getOutput() );
        if ( !output.exists() )
        {
            throw new IllegalStateException( "Output file doesn't exist and no error was thrown by the compiler!" );
        }

        if ( getLocalesRuntime() != null )
        {
            List<Result> results = new ArrayList<Result>();
            for ( String locale : getLocalesRuntime() )
            {
                MxmlcMojo cfg = this.clone();
                configureResourceBundle( locale, cfg );
                results.add( executeCompiler( new MxmlcConfigurationHolder( cfg, null ), fullSynchronization ) );
            }

            wait( results );
        }

        if ( getModules() != null )
        {
            List<Result> results = new ArrayList<Result>();

            for ( Module module : getModules() )
            {
                if ( module.isOptimize() == null )
                {
                    module.setOptimize( modulesLoadExterns );
                }

                File moduleSource =
                    SourceFileResolver.resolveSourceFile( project.getCompileSourceRoots(), module.getSourceFile() );

                String classifier = FilenameUtils.getBaseName( moduleSource.getName() ).toLowerCase();

                String moduleFinalName;
                if ( module.getFinalName() != null )
                {
                    moduleFinalName = module.getFinalName();
                }
                else
                {
                    moduleFinalName = project.getBuild().getFinalName() + "-" + classifier;
                }

                File moduleOutputDir;
                if ( module.getDestinationPath() != null )
                {
                    moduleOutputDir = new File( project.getBuild().getDirectory(), module.getDestinationPath() );
                }
                else
                {
                    moduleOutputDir = new File( project.getBuild().getDirectory() );
                }

                List<String> loadExterns = new ArrayList<String>();
                loadExterns.add( getLinkReport() );
                if ( getLoadExterns() != null )
                {
                    loadExterns.addAll( Arrays.asList( getLoadExterns() ) );
                }

                MxmlcMojo cfg = this.clone();
                cfg.classifier = classifier;
                cfg.targetDirectory = moduleOutputDir;
                cfg.finalName = moduleFinalName;
                if ( module.isOptimize() )
                {
                    cfg.getCache().put( LOAD_EXTERNS, loadExterns.toArray( new String[1] ) );
                }
                cfg.getCache().put( RUNTIME_SHARED_LIBRARY_PATH, null );
                cfg.getCache().put( INCLUDE_LIBRARIES, null );
                cfg.getCache().put( EXTERNAL_LIBRARY_PATH, getModulesExternalLibraryPath() );
                results.add( executeCompiler( new MxmlcConfigurationHolder( cfg, moduleSource ), fullSynchronization ) );
            }

            wait( results );
        }
    }

    public List<String> getFileSpecs()
    {
        return fileSpecs;
    }

    public List<String> getIncludeResourceBundles()
    {
        return includeResourceBundles;
    }

    @Override
    public String[] getLocale()
    {
        String[] locales = super.getLocale();
        if ( locales != null )
        {
            return locales;
        }

        if ( "css".equalsIgnoreCase( FilenameUtils.getExtension( sourceFile ) ) )
        {
            return new String[] {};
        }

        return new String[] { toolsLocale };

    }

    public Module[] getModules()
    {
        return modules;
    }

    @SuppressWarnings( "unchecked" )
    private File[] getModulesExternalLibraryPath()
    {
        return MavenUtils.getFiles( getDependencies( not( GLOBAL_MATCHER ),//
                                                     allOf( type( SWC ),//
                                                            anyOf( scope( EXTERNAL ), scope( CACHING ), scope( RSL ),
                                                                   scope( INTERNAL ) ) ) ), getGlobalArtifactCollection() );
    }

    public String getProjector()
    {
        return projector;
    }

    @Override
    public final String getProjectType()
    {
        return SWF;
    }

    protected File getSourceFile()
    {
        return SourceFileResolver.resolveSourceFile( project.getCompileSourceRoots(), sourceFile, project.getGroupId(),
                                                     project.getArtifactId() );
    }

    public boolean isUpdateSecuritySandbox()
    {
        return updateSecuritySandbox;
    }

    @Override
    protected Artifact getGlobalArtifact() {
        if("FlexJS".equals(compilerName)) {
            Artifact global = getDependency(GLOBAL_MATCHER);
            if(global != null) {
                return super.getGlobalArtifact();
            }
            return null;
        }
        return super.getGlobalArtifact();
    }

}
