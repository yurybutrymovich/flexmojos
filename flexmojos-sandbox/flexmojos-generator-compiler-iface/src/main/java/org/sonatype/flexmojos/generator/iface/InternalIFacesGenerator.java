package org.sonatype.flexmojos.generator.iface;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.apache.ws.jaxme.js.JavaMethod;
import org.apache.ws.jaxme.js.JavaQName;
import org.apache.ws.jaxme.js.JavaQNameImpl;
import org.apache.ws.jaxme.js.JavaSource;
import org.apache.ws.jaxme.js.JavaSourceFactory;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.sonatype.flexmojos.generator.api.GenerationException;
import org.sonatype.flexmojos.generator.api.GenerationRequest;
import org.sonatype.flexmojos.generator.api.Generator;

import flex2.compiler.config.ConfigurationInfo;
import flex2.compiler.config.ConfigurationValue;

@Component( role = Generator.class, hint = "internal-ifaces" )
public final class InternalIFacesGenerator
    extends AbstractLogEnabled
    implements Generator
{

    private static final String PACKAGE = "org.sonatype.flexmojos.compiler";

    private static final String SET_PREFIX = "cfg";

    private static final String GET_PREFIX = "get";

    private static final String CONFIGURATION_SUFFIX = "Configuration";

    private static final String INFO_SUFFIX = "Info";

    public InternalIFacesGenerator()
    {
    }

    public void generate( GenerationRequest request )
        throws GenerationException
    {

        for ( String classname : request.getClasses().keySet() )
        {
            Class<?> clazz;
            try
            {
                clazz = request.getClassLoader().loadClass( classname );
            }
            catch ( ClassNotFoundException e )
            {
                throw new GenerationException( e.getMessage(), e );
            }

            JavaSourceFactory factory = new JavaSourceFactory();

            getMethods( clazz, factory );

            File outDir = request.getTransientOutputFolder();
            outDir.mkdirs();

            try
            {
                factory.write( outDir );
            }
            catch ( Exception e )
            {
                throw new GenerationException( "Error generating " + clazz.getName(), e );
            }
        }
    }

    private String varname( String membername, String basename )
    {
        return ( ( basename == null ) ? membername : ( basename + "." + membername ) );
    }

    private JavaQName getMethods( Class<?> clazz, JavaSourceFactory factory )
    {
        JavaQName className = JavaQNameImpl.getInstance( PACKAGE, "I" + clazz.getSimpleName() );
        JavaSource js = factory.newJavaSource( className, "public" );
        js.setType( JavaSource.INTERFACE );

        Method methods[] = clazz.getMethods();
        for ( int m = 0; m < methods.length; ++m )
        {
            Method method = methods[m];

            if ( method.getName().startsWith( SET_PREFIX ) )
            {
                Class<?>[] pt = method.getParameterTypes();

                if ( ( pt.length > 1 ) && ( pt[0] == ConfigurationValue.class ) )
                {
                    // This is an autoconfiguration setter!

                    ConfigurationInfo info = createInfo( method );

                    String leafname = method.getName().substring( SET_PREFIX.length() );
                    String name = varname( leafname, null );

                    JavaQName type;
                    int args = info.getArgCount();
                    if ( args == 0 )
                    {
                        continue;
                    }
                    else if ( args == 1 )
                    {
                        type = JavaQNameImpl.getInstance( getArgType( info, 0 ) );
                    }
                    else if ( args < 0 )
                    {
                        type = JavaQNameImpl.getInstance( method.getParameterTypes()[1] );
                    }
                    else
                    {
                        type = JavaQNameImpl.getInstance( PACKAGE, "I" + name );
                        JavaSource subClass = factory.newJavaSource( type, "public" );
                        subClass.setType( JavaSource.INTERFACE );
                        for ( int i = 0; i < args; i++ )
                        {
                            Class<?> argType = getArgType( info, i );
                            String argName = info.getArgName( i );
                            argName = StringUtil.toCamelCase( argName );
                            subClass.newJavaMethod( argName, argType );
                        }
                    }

                    if ( info.isPath() )
                    {
                        type = JavaQNameImpl.getInstance( File.class );
                    }

                    if ( info.allowMultiple()
                        && !( type.isArray() || type.equals( JavaQNameImpl.getInstance( List.class ) ) ) )
                    {
                        type = JavaQNameImpl.getArray( type );
                    }

                    JavaMethod nm = js.newJavaMethod( GET_PREFIX + name, type );
                }
            }
        }

        // Now find all children.
        for ( int m = 0; m < methods.length; ++m )
        {
            Method method = methods[m];

            String name = method.getName();
            if ( name.startsWith( GET_PREFIX ) && name.endsWith( CONFIGURATION_SUFFIX ) )
            {
                JavaQName source = getMethods( method.getReturnType(), factory );
                js.newJavaMethod( name, source );
            }
            else
            {
                continue;
            }
        }

        return className;
    }

    private Class<?> getArgType( ConfigurationInfo info, int i )
    {
        Class<?> argType;
        try
        {
            argType = info.getArgType( i );
        }
        catch ( NullPointerException e )
        {
            argType = String.class;
        }
        return argType;
    }

    private static ConfigurationInfo createInfo( Method setterMethod )
    {
        ConfigurationInfo info = null;

        String infoMethodName = GET_PREFIX + setterMethod.getName().substring( SET_PREFIX.length() ) + INFO_SUFFIX;
        Class<?> cfgClass = setterMethod.getDeclaringClass();

        try
        {
            Method infoMethod = cfgClass.getMethod( infoMethodName, (Class[]) null );

            if ( !Modifier.isStatic( infoMethod.getModifiers() ) )
            {
                assert false : ( "coding error: " + cfgClass.getName() + "." + infoMethodName + " needs to be static!" );
                infoMethod = null;
            }

            info = (ConfigurationInfo) infoMethod.invoke( null, (Object[]) null );

        }
        catch ( Exception e )
        {
        }

        if ( info == null )
        {
            info = new ConfigurationInfo();
        }
        // info.setSetterMethod( setterMethod );
        // info.setGetterMethod( getterMethod );

        return info;
    }

    private Class<?>[] simplify( Class<?>[] args )
    {
        Class<?>[] simpleArgs = new Class<?>[args.length];
        for ( int i = 0; i < args.length; i++ )
        {
            Class<?> clazz = args[i];
            if ( !clazz.isArray() )
            {
                simpleArgs[i] = clazz;
            }
            else
            {
                simpleArgs[i] = clazz.getComponentType();
            }
        }
        return simpleArgs;
    }

}