/**
 * Flexmojos is a set of maven goals to allow maven users to compile, optimize and test Flex SWF, Flex SWC, Air SWF and Air SWC.
 * Copyright (C) 2008-2012  Marvin Froeder <marvin@flexmojos.net>
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.flexmojos.oss.plugin.air.packager;

import org.codehaus.plexus.component.annotations.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component(role = net.flexmojos.oss.plugin.air.packager.Packager.class, hint = "air")
public class AirPackager extends BasePackager {

    @Override
    protected List<String> getAdtCommand() {
        // Get the java command.
        List<String> command = new ArrayList<String>();
        if (System.getProperty("os.name").startsWith("Win")) {
            command.add(System.getProperties().getProperty("java.home") + File.separator + "bin" +
                    File.separator + "java.exe");
        } else {
            command.add(System.getProperties().getProperty("java.home") + File.separator + "bin" +
                    File.separator + "java");
        }
        command.add("-jar");
        File adtJar = new File(request.getWorkDir(), "lib/adt.jar");
        command.add(adtJar.getAbsolutePath());
        return command;
    }

    @Override
    public File execute() throws PackagingException {
        File outputFile = new File(request.getBuildDir(), request.getFinalName() +
                ((request.getClassifier() != null) ? "-" + request.getClassifier() : "")  +".air");
        request.setOutputFile(outputFile);

        List<String> adtArgs = new ArrayList<String>();
        adtArgs.add("-package");
        adtArgs.add("-storetype");
        adtArgs.add(request.getStoretype());
        adtArgs.add("-keystore");
        adtArgs.add(request.getStorefile().getAbsolutePath());
        adtArgs.add("-storepass");
        adtArgs.add(request.getStorepass());
        adtArgs.add("-keypass");
        adtArgs.add(request.getStorepass());
        adtArgs.add(request.getOutputFile().getAbsolutePath());
        adtArgs.add(request.getDescriptorFile().getAbsolutePath());
        adtArgs.add(request.getInputFile().getName());
        runAdt(adtArgs);

        if(!outputFile.exists()) {
            throw new PackagingException("Output file does not exist " + outputFile.getAbsolutePath());
        }

        return outputFile;
    }

}
