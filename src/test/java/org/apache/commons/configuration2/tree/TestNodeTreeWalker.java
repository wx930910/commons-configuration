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
package org.apache.commons.configuration2.tree;

import static org.junit.Assert.assertEquals;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;

/**
 * Test class for {@code NodeTreeWalker}.
 *
 */
public class TestNodeTreeWalker {
	/**
	 * Generates a name which indicates that the corresponding node was visited
	 * after its children.
	 *
	 * @param name the node name to be decorated
	 * @return the name with the after indicator
	 */
	private static String visitAfterName(final String name) {
		return "->" + name;
	}

	/**
	 * Creates a dummy node handler.
	 *
	 * @return the node handler
	 */
	private static NodeHandler<ImmutableNode> createHandler() {
		return new InMemoryNodeModel().getNodeHandler();
	}

	/**
	 * Tests a traversal in BFS mode.
	 */
	@Test
	public void testWalkBFS() throws Exception {
		final List<String> expected = expectBFS();
		final ConfigurationNodeVisitor<ImmutableNode> visitor = Mockito.mock(ConfigurationNodeVisitor.class);
		List<String> visitorVisitedNodes = new LinkedList<>();
		int visitorMaxNodeCount = Integer.MAX_VALUE;
		Mockito.doAnswer((stubInvo) -> {
			ImmutableNode node = stubInvo.getArgument(0);
			NodeHandler<ImmutableNode> handler = stubInvo.getArgument(1);
			visitorVisitedNodes.add(handler.nodeName(node));
			return null;
		}).when(visitor).visitBeforeChildren(Mockito.any(), Mockito.any());
		Mockito.when(visitor.terminate()).thenAnswer((stubInvo) -> {
			return visitorVisitedNodes.size() >= visitorMaxNodeCount;
		});
		Mockito.doAnswer((stubInvo) -> {
			ImmutableNode node = stubInvo.getArgument(0);
			NodeHandler<ImmutableNode> handler = stubInvo.getArgument(1);
			visitorVisitedNodes.add(visitAfterName(handler.nodeName(node)));
			return null;
		}).when(visitor).visitAfterChildren(Mockito.any(), Mockito.any());
		NodeTreeWalker.INSTANCE.walkBFS(NodeStructureHelper.ROOT_AUTHORS_TREE, visitor, createHandler());
		assertEquals("Wrong visited nodes", expected, visitorVisitedNodes);
	}

	/**
	 * Prepares a list with the names of nodes encountered during a BFS walk.
	 *
	 * @return the expected node names in BFS mode
	 */
	private List<String> expectBFS() {
		final List<String> expected = new LinkedList<>();
		final List<String> works = new LinkedList<>();
		final List<String> personae = new LinkedList<>();
		expected.add(NodeStructureHelper.ROOT_AUTHORS_TREE.getNodeName());
		for (int authorIdx = 0; authorIdx < NodeStructureHelper.authorsLength(); authorIdx++) {
			expected.add(NodeStructureHelper.author(authorIdx));
			for (int workIdx = 0; workIdx < NodeStructureHelper.worksLength(authorIdx); workIdx++) {
				works.add(NodeStructureHelper.work(authorIdx, workIdx));
				for (int personIdx = 0; personIdx < NodeStructureHelper.personaeLength(authorIdx,
						workIdx); personIdx++) {
					personae.add(NodeStructureHelper.persona(authorIdx, workIdx, personIdx));
				}
			}
		}
		expected.addAll(works);
		expected.addAll(personae);
		return expected;
	}
}
