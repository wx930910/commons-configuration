/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.configuration.web;

import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.DefaultListDelimiterHandler;
import org.apache.commons.configuration.TestAbstractConfiguration;
import org.junit.Test;

/**
 * Test case for the {@link ServletFilterConfiguration} class.
 *
 * @author Emmanuel Bourg
 * @version $Id$
 */
public class TestServletFilterConfiguration extends TestAbstractConfiguration
{
    @Override
    protected AbstractConfiguration getConfiguration()
    {
        MockFilterConfig config = new MockFilterConfig();
        config.setInitParameter("key1", "value1");
        config.setInitParameter("key2", "value2");
        config.setInitParameter("list", "value1, value2");
        config.setInitParameter("listesc", "value1\\,value2");

        ServletFilterConfiguration resultConfig = new ServletFilterConfiguration(config);
        resultConfig.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        return resultConfig;
    }

    @Override
    protected AbstractConfiguration getEmptyConfiguration()
    {
        return new ServletFilterConfiguration(new MockFilterConfig());
    }

    private class MockFilterConfig implements FilterConfig
    {
        private Properties parameters = new Properties();

        public String getFilterName()
        {
            return null;
        }

        public ServletContext getServletContext()
        {
            return null;
        }

        public String getInitParameter(String key)
        {
            return parameters.getProperty(key);
        }

        public Enumeration<?> getInitParameterNames()
        {
            return parameters.keys();
        }

        public void setInitParameter(String key, String value)
        {
            parameters.setProperty(key, value);
        }
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void testAddPropertyDirect()
    {
        super.testAddPropertyDirect();
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void testClearProperty()
    {
        super.testClearProperty();
    }
}
