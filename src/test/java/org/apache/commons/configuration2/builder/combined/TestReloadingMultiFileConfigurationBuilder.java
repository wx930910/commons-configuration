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
package org.apache.commons.configuration2.builder.combined;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.BasicBuilderParameters;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.ReloadingFileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.XMLBuilderParametersImpl;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.reloading.ReloadingController;
import org.apache.commons.configuration2.tree.ExpressionEngine;
import org.apache.commons.configuration2.tree.xpath.XPathExpressionEngine;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Test class for {@code ReloadingMultiFileConfigurationBuilder}.
 *
 */
public class TestReloadingMultiFileConfigurationBuilder extends AbstractMultiFileConfigurationBuilderTest {
	/**
	 * Tests whether parameters passed to the constructor are passed to the super
	 * class.
	 */
	@Test
	public void testInitWithParameters() throws ConfigurationException {
		final ExpressionEngine engine = new XPathExpressionEngine();
		final BasicBuilderParameters params = createTestBuilderParameters(
				new XMLBuilderParametersImpl().setExpressionEngine(engine));
		final ReloadingMultiFileConfigurationBuilder<XMLConfiguration> builder = new ReloadingMultiFileConfigurationBuilder<>(
				XMLConfiguration.class, params.getParameters());
		switchToConfig(1);
		final XMLConfiguration config = builder.getConfiguration();
		assertSame("Expression engine not set", engine, config.getExpressionEngine());
	}

	/**
	 * Tests whether correct managed builders are created.
	 */
	@Test
	public void testCreateManagedBuilder() throws ConfigurationException {
		final ReloadingMultiFileConfigurationBuilder<XMLConfiguration> builder = new ReloadingMultiFileConfigurationBuilder<>(
				XMLConfiguration.class);
		final FileBasedConfigurationBuilder<XMLConfiguration> managedBuilder = builder.createManagedBuilder("test.xml",
				createTestBuilderParameters(null).getParameters());
		assertTrue("Not a reloading builder", managedBuilder instanceof ReloadingFileBasedConfigurationBuilder);
		assertFalse("Wrong flag value", managedBuilder.isAllowFailOnInit());
	}

	/**
	 * Tests whether the allowFailOnInit flag is passed to newly created managed
	 * builders.
	 */
	@Test
	public void testCreateManagedBuilderWithAllowFailFlag() throws ConfigurationException {
		final ReloadingMultiFileConfigurationBuilder<XMLConfiguration> builder = new ReloadingMultiFileConfigurationBuilder<>(
				XMLConfiguration.class, null, true);
		final FileBasedConfigurationBuilder<XMLConfiguration> managedBuilder = builder.createManagedBuilder("test.xml",
				createTestBuilderParameters(null).getParameters());
		assertTrue("Wrong flag value", managedBuilder.isAllowFailOnInit());
	}

	/**
	 * Tests whether a reloading check works correctly.
	 */
	@Test
	public void testReloadingControllerCheck() throws ConfigurationException, Exception {
		final ReloadingMultiFileConfigurationBuilder<XMLConfiguration> builder = spy(
				new ReloadingMultiFileConfigurationBuilder(XMLConfiguration.class,
						createTestBuilderParameters(null).getParameters()));
		List<ReloadingController> builderReloadingControllers;
		builderReloadingControllers = new ArrayList<>();
		doAnswer((stubInvo) -> {
			Map<String, Object> params = stubInvo.getArgument(1);
			final ReloadingController ctrl = EasyMock.createMock(ReloadingController.class);
			builderReloadingControllers.add(ctrl);
			return new ReloadingFileBasedConfigurationBuilder<XMLConfiguration>(builder.getResultClass(), params) {
				@Override
				public ReloadingController getReloadingController() {
					return ctrl;
				}
			};
		}).when(builder).createManagedBuilder(any(), any());
		switchToConfig(1);
		builder.getConfiguration();
		switchToConfig(2);
		builder.getConfiguration();
		final List<ReloadingController> controllers = builderReloadingControllers;
		assertEquals("Wrong number of reloading controllers", 2, controllers.size());
		EasyMock.reset(controllers.toArray());
		for (final ReloadingController c : controllers) {
			EasyMock.expect(c.checkForReloading(null)).andReturn(Boolean.FALSE);
		}
		EasyMock.replay(controllers.toArray());
		assertFalse("Wrong result", builder.getReloadingController().checkForReloading(this));
		EasyMock.verify(controllers.toArray());
	}

	/**
	 * Tests a reloading check which detects the need to reload.
	 */
	@Test
	public void testReloadingControllerCheckReloadingRequired() throws ConfigurationException, Exception {
		final ReloadingMultiFileConfigurationBuilder<XMLConfiguration> builder = spy(
				new ReloadingMultiFileConfigurationBuilder(XMLConfiguration.class,
						createTestBuilderParameters(null).getParameters()));
		List<ReloadingController> builderReloadingControllers;
		builderReloadingControllers = new ArrayList<>();
		doAnswer((stubInvo) -> {
			Map<String, Object> params = stubInvo.getArgument(1);
			final ReloadingController ctrl = EasyMock.createMock(ReloadingController.class);
			builderReloadingControllers.add(ctrl);
			return new ReloadingFileBasedConfigurationBuilder<XMLConfiguration>(builder.getResultClass(), params) {
				@Override
				public ReloadingController getReloadingController() {
					return ctrl;
				}
			};
		}).when(builder).createManagedBuilder(any(), any());
		for (int i = 1; i <= 3; i++) {
			switchToConfig(i);
			builder.getConfiguration();
		}
		final List<ReloadingController> controllers = builderReloadingControllers;
		EasyMock.reset(controllers.toArray());
		EasyMock.expect(controllers.get(0).checkForReloading(null)).andReturn(Boolean.FALSE).anyTimes();
		EasyMock.expect(controllers.get(1).checkForReloading(null)).andReturn(Boolean.TRUE);
		EasyMock.expect(controllers.get(2).checkForReloading(null)).andReturn(Boolean.FALSE).anyTimes();
		EasyMock.replay(controllers.toArray());
		assertTrue("Wrong result", builder.getReloadingController().checkForReloading(this));
		EasyMock.verify(controllers.toArray());
	}

	/**
	 * Tests whether the reloading state of the reloading controller can be reset.
	 */
	@Test
	public void testReloadingControllerResetReloadingState() throws ConfigurationException, Exception {
		final ReloadingMultiFileConfigurationBuilder<XMLConfiguration> builder = spy(
				new ReloadingMultiFileConfigurationBuilder(XMLConfiguration.class,
						createTestBuilderParameters(null).getParameters()));
		List<ReloadingController> builderReloadingControllers;
		builderReloadingControllers = new ArrayList<>();
		doAnswer((stubInvo) -> {
			Map<String, Object> params = stubInvo.getArgument(1);
			final ReloadingController ctrl = EasyMock.createMock(ReloadingController.class);
			builderReloadingControllers.add(ctrl);
			return new ReloadingFileBasedConfigurationBuilder<XMLConfiguration>(builder.getResultClass(), params) {
				@Override
				public ReloadingController getReloadingController() {
					return ctrl;
				}
			};
		}).when(builder).createManagedBuilder(any(), any());
		switchToConfig(1);
		builder.getConfiguration();
		switchToConfig(2);
		builder.getConfiguration();
		final List<ReloadingController> controllers = builderReloadingControllers;
		EasyMock.reset(controllers.toArray());
		for (final ReloadingController c : controllers) {
			EasyMock.expect(c.checkForReloading(null)).andReturn(Boolean.TRUE).anyTimes();
			c.resetReloadingState();
		}
		EasyMock.replay(controllers.toArray());
		builder.getReloadingController().checkForReloading(null);
		builder.getReloadingController().resetReloadingState();
		EasyMock.verify(controllers.toArray());
	}
}
