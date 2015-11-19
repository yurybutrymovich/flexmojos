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
package net.flexmojos.oss.tests.issues;

import net.flexmojos.oss.matcher.file.FileMatcher;
import net.flexmojos.oss.test.FMVerifier;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsCollectionContaining;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Flexmojos334Test
    extends AbstractIssueTest
{

    @Test
    public void linkReportScanner()
        throws Exception
    {
        FMVerifier v =
            testIssue( "flexmojos-334", "-Dflex.coverageStrategy=link-report", "-Dflex.coverageOverwriteSourceRoots=src/main/flex" );
        List<String> classes = getCoveredClasses( v );
        Assert.assertEquals( classes.size(), 3, classes.toString() );
        MatcherAssert.assertThat( classes, IsCollectionContaining.hasItems( "FlexMaven/UntestedClass.as",
                                                                            "FlexMaven/sampleInclude.as",
                                                                            "FlexMaven/App.as" ) );
        MatcherAssert.assertThat( classes,
                                  CoreMatchers.not( IsCollectionContaining.hasItems( "FlexMaven/unusedInclude.java" ) ) );
    }

    @Test
    public void disabledScanner()
        throws Exception
    {
        FMVerifier v =
            testIssue( "flexmojos-334", "-Dflex.coverageStrategy=disabled", "-Dflex.coverageOverwriteSourceRoots=src/main/flex" );
        List<String> classes = getCoveredClasses( v );
        Assert.assertEquals( classes.size(), 2, classes.toString() );
        MatcherAssert.assertThat( classes,
                                  IsCollectionContaining.hasItems( "FlexMaven/sampleInclude.as", "FlexMaven/App.as" ) );
        MatcherAssert.assertThat( classes,
                                  CoreMatchers.not( IsCollectionContaining.hasItems( "FlexMaven/UntestedClass.as",
                                                                                     "FlexMaven/unusedInclude.as" ) ) );
    }

    // Disabled for now, because the Issue https://flexmojos.atlassian.net/browse/FLEXMOJOS-525 is still open.
    //@Test
    public void as3contentScanner()
        throws Exception
    {
        FMVerifier v =
            testIssue( "flexmojos-334", "-Dflex.coverageStrategy=as3Content", "-Dflex.coverageOverwriteSourceRoots=src/main/flex" );
        List<String> classes = getCoveredClasses( v );
        Assert.assertEquals( classes.size(), 4, classes.toString() );
        MatcherAssert.assertThat( classes, IsCollectionContaining.hasItems( "FlexMaven/UntestedClass.as",
                                                                            "FlexMaven/sampleInclude.as",
                                                                            "FlexMaven/unusedInclude.as",
                                                                            "FlexMaven/App.as" ) );
    }

    @SuppressWarnings( "unchecked" )
    private List<String> getCoveredClasses( FMVerifier v )
        throws DocumentException
    {
        File coverageXml = new File( v.getBasedir(), "target/coverage/coverage.xml" );

        MatcherAssert.assertThat( coverageXml, FileMatcher.exists() );

        List<String> linkedFiles = new ArrayList<String>();
        try {
            SAXReader saxReader = new SAXReader();
            // Disable DTD validation (Needed because of inavailalbity of sonatype servers in the time aroung 19.07.2015
            saxReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            Document document = saxReader.read(coverageXml);

            List<Attribute> list = document.selectNodes("//class/@filename");
            for (Attribute attribute : list) {
                linkedFiles.add(attribute.getValue());
            }
        } catch (SAXException e) {
            throw new RuntimeException("Error parsing coverage report", e);
        }
        return linkedFiles;
    }
}
