package org.dllearner.configuration.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Chris
 * Date: 8/23/11
 * Time: 4:57 AM
 * Interface for building an application context for use with DL-Learner interfaces.
 */
public interface ApplicationContextBuilder {


    /**
     * Create an application context for use with the DL-Learner CLI interface.
     * <p/>
     * Note: In case of multiple spring config file locations, later bean definitions will override ones defined in earlier loaded files. This can be leveraged to deliberately override certain bean definitions via an extra XML file.
     *
     * @param confFile                     The DL-Learner Configuration File.
     * @param componentKeyPrefixes         The List of Strings which indicate a component/bean reference in the configuration file. (e.g. 'component:', ':', etc.)
     * @param springConfigurationLocations An ordered list of Spring Configuration Files - beans in later files can override beans in earlier files.
     * @return An Application Context
     * @throws IOException If there's a problem reading any of the files.
     */
    public ApplicationContext buildApplicationContext(Resource confFile, List<String> componentKeyPrefixes, List<Resource> springConfigurationLocations) throws IOException;

}
