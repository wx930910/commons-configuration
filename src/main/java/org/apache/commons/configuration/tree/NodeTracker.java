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
package org.apache.commons.configuration.tree;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.ex.ConfigurationRuntimeException;

/**
 * <p>
 * A class which can track specific nodes in an {@link InMemoryNodeModel}.
 * </p>
 * <p>
 * Sometimes it is necessary to keep track on a specific node, for instance when
 * operating on a subtree of a model. For a model comprised of immutable nodes
 * this is not trivial because each update of the model may cause the node to be
 * replaced. So holding a direct pointer onto the target node is not an option;
 * this instance may become outdated.
 * </p>
 * <p>
 * This class provides an API for selecting a specific node by using a
 * {@link NodeSelector}. The selector is used to obtain an initial reference to
 * the target node. It is then applied again after each update of the associated
 * node model (which is done in the {@code update()} method). At this point of
 * time two things can happen:
 * <ul>
 * <li>The {@code NodeSelector} associated with the tracked node still selects a
 * single node. Then this node becomes the new tracked node. This may be the
 * same instance as before or a new one.</li>
 * <li>The selector does no longer find the target node. This can happen for
 * instance if it has been removed by an operation. In this case, the previous
 * node instance is used. It is now detached from the model, but can still be
 * used for operations on this subtree. It may even become life again after
 * another update of the model.</li>
 * </ul>
 * </p>
 * <p>
 * Implementation note: This class is intended to work in a concurrent
 * environment. Instances are immutable. The represented state can be updated by
 * creating new instances which are then stored by the owning node model.
 * </p>
 *
 * @version $Id$
 * @since 2.0
 */
class NodeTracker
{
    /** A map with data about tracked nodes. */
    private final Map<NodeSelector, TrackedNodeData> trackedNodes;

    /**
     * Creates a new instance of {@code NodeTracker}. This instance does not yet
     * track any nodes.
     */
    public NodeTracker()
    {
        this(Collections.<NodeSelector, TrackedNodeData> emptyMap());
    }

    /**
     * Creates a new instance of {@code NodeTracker} and initializes it with the
     * given map of tracked nodes. This constructor is used internally when the
     * state of tracked nodes has changed.
     *
     * @param map the map with tracked nodes
     */
    private NodeTracker(Map<NodeSelector, TrackedNodeData> map)
    {
        trackedNodes = map;
    }

    /**
     * Adds a node to be tracked. The passed in selector must select exactly one
     * target node, otherwise an exception is thrown. A new instance is created
     * with the updated tracking state.
     *
     * @param root the root node
     * @param selector the {@code NodeSelector}
     * @param resolver the {@code NodeKeyResolver}
     * @param handler the {@code NodeHandler}
     * @return the updated instance
     * @throws ConfigurationRuntimeException if the selector does not select a
     *         single node
     */
    public NodeTracker trackNode(ImmutableNode root, NodeSelector selector,
            NodeKeyResolver<ImmutableNode> resolver,
            NodeHandler<ImmutableNode> handler)
    {
        Map<NodeSelector, TrackedNodeData> newState =
                new HashMap<NodeSelector, TrackedNodeData>(trackedNodes);
        TrackedNodeData trackData = newState.get(selector);
        newState.put(
                selector,
                trackDataForAddedObserver(root, selector, resolver, handler,
                        trackData));
        return new NodeTracker(newState);
    }

    /**
     * Notifies this object that an observer was removed for the specified
     * tracked node. If this was the last observer, the track data for this
     * selector can be removed.
     *
     * @param selector the {@code NodeSelector}
     * @return the updated instance
     * @throws ConfigurationRuntimeException if no information about this node
     *         is available
     */
    public NodeTracker untrackNode(NodeSelector selector)
    {
        TrackedNodeData trackData = getTrackedNodeData(selector);

        Map<NodeSelector, TrackedNodeData> newState =
                new HashMap<NodeSelector, TrackedNodeData>(trackedNodes);
        TrackedNodeData newTrackData = trackData.observerRemoved();
        if (newTrackData == null)
        {
            newState.remove(selector);
        }
        else
        {
            newState.put(selector, newTrackData);
        }
        return new NodeTracker(newState);
    }

    /**
     * Returns the current {@code ImmutableNode} instance associated with the
     * given selector.
     *
     * @param selector the {@code NodeSelector}
     * @return the {@code ImmutableNode} selected by this selector
     * @throws ConfigurationRuntimeException if no data for this selector is
     *         available
     */
    public ImmutableNode getTrackedNode(NodeSelector selector)
    {
        return getTrackedNodeData(selector).getNode();
    }

    /**
     * Returns a flag whether the specified tracked node is detached.
     *
     * @param selector the {@code NodeSelector}
     * @return a flag whether this node is detached
     * @throws ConfigurationRuntimeException if no data for this selector is
     *         available
     */
    public boolean isTrackedNodeDetached(NodeSelector selector)
    {
        return getTrackedNodeData(selector).isDetached();
    }

    /**
     * Updates tracking information after the node structure has been changed.
     * This method iterates over all tracked nodes. The selectors are evaluated
     * again to update the node reference. If this fails for a selector, the
     * previous node is reused; this tracked node is then detached.
     *
     * @param root the root node
     * @param resolver the {@code NodeKeyResolver}
     * @param handler the {@code NodeHandler}
     * @return the updated instance
     */
    public NodeTracker update(ImmutableNode root,
            NodeKeyResolver<ImmutableNode> resolver,
            NodeHandler<ImmutableNode> handler)
    {
        if (trackedNodes.isEmpty())
        {
            // there is not state to be updated
            return this;
        }

        Map<NodeSelector, TrackedNodeData> newState =
                new HashMap<NodeSelector, TrackedNodeData>();
        for (Map.Entry<NodeSelector, TrackedNodeData> e : trackedNodes
                .entrySet())
        {
            newState.put(e.getKey(), determineUpdatedTrackedNodeData(root, resolver, handler, e));
        }

        return new NodeTracker(newState);
    }

    /**
     * Marks all tracked nodes as detached. This method is called if there are
     * some drastic changes on the underlying node structure, e.g. if the root
     * node was replaced.
     *
     * @return the updated instance
     */
    public NodeTracker detachAllTrackedNodes()
    {
        if (trackedNodes.isEmpty())
        {
            // there is not state to be updated
            return this;
        }

        Map<NodeSelector, TrackedNodeData> newState =
                new HashMap<NodeSelector, TrackedNodeData>();
        for (Map.Entry<NodeSelector, TrackedNodeData> e : trackedNodes
                .entrySet())
        {
            TrackedNodeData newData =
                    e.getValue().isDetached() ? e.getValue() : e.getValue()
                            .detach();
            newState.put(e.getKey(), newData);
        }

        return new NodeTracker(newState);
    }

    /**
     * Returns a {@code TrackedNodeData} object for an update operation. If the
     * tracked node is still life, its selector is applied to the current root
     * node. It may become detached if there is no match.
     *
     * @param root the root node
     * @param resolver the {@code NodeKeyResolver}
     * @param handler the {@code NodeHandler}
     * @param e the current selector and {@code TrackedNodeData}
     * @return the updated {@code TrackedNodeData}
     */
    private TrackedNodeData determineUpdatedTrackedNodeData(ImmutableNode root,
            NodeKeyResolver<ImmutableNode> resolver,
            NodeHandler<ImmutableNode> handler,
            Map.Entry<NodeSelector, TrackedNodeData> e)
    {
        if (e.getValue().isDetached())
        {
            return e.getValue();
        }
        ImmutableNode newTarget = e.getKey().select(root, resolver, handler);
        return (newTarget != null) ? e.getValue().updateNode(newTarget) : e
                .getValue().detach();
    }

    /**
     * Obtains the {@code TrackedNodeData} object for the specified selector. If
     * the selector cannot be resolved, an exception is thrown.
     *
     * @param selector the {@code NodeSelector}
     * @return the {@code TrackedNodeData} object for this selector
     * @throws ConfigurationRuntimeException if the selector cannot be resolved
     */
    private TrackedNodeData getTrackedNodeData(NodeSelector selector)
    {
        TrackedNodeData trackData = trackedNodes.get(selector);
        if (trackData == null)
        {
            throw new ConfigurationRuntimeException("No tracked node found: "
                    + selector);
        }
        return trackData;
    }

    /**
     * Creates a {@code TrackedNodeData} object for a newly added observer for
     * the specified node selector.
     *
     * @param root the root node
     * @param selector the {@code NodeSelector}
     * @param resolver the {@code NodeKeyResolver}
     * @param handler the {@code NodeHandler}
     * @param trackData the current data for this selector
     * @return the updated {@code TrackedNodeData}
     * @throws ConfigurationRuntimeException if the selector does not select a
     *         single node
     */
    private static TrackedNodeData trackDataForAddedObserver(
            ImmutableNode root, NodeSelector selector,
            NodeKeyResolver<ImmutableNode> resolver,
            NodeHandler<ImmutableNode> handler, TrackedNodeData trackData)
    {
        if (trackData != null)
        {
            return trackData.observerAdded();
        }
        else
        {
            ImmutableNode target = selector.select(root, resolver, handler);
            if (target == null)
            {
                throw new ConfigurationRuntimeException(
                        "Selector does not select unique node: " + selector);
            }
            return new TrackedNodeData(target);
        }
    }

    /**
     * A simple data class holding information about a tracked node.
     */
    private static class TrackedNodeData
    {
        /** The current instance of the tracked node. */
        private final ImmutableNode node;

        /** The number of observers of this tracked node. */
        private final int observerCount;

        /** A flag whether the node is detached. */
        private final boolean detached;

        /**
         * Creates a new instance of {@code TrackedNodeData} and initializes it
         * with the current reference to the tracked node.
         *
         * @param nd the tracked node
         */
        public TrackedNodeData(ImmutableNode nd)
        {
            this(nd, 1, false);
        }

        /**
         * Creates a new instance of {@code TrackedNodeData} and initializes its
         * properties.
         *
         * @param nd the tracked node
         * @param obsCount the observer count
         * @param isDetached a flag whether the node is detached
         */
        private TrackedNodeData(ImmutableNode nd, int obsCount,
                boolean isDetached)
        {
            node = nd;
            observerCount = obsCount;
            detached = isDetached;
        }

        /**
         * Returns the tracked node.
         *
         * @return the tracked node
         */
        public ImmutableNode getNode()
        {
            return node;
        }

        /**
         * Returns a flag whether the represented tracked node is detached.
         *
         * @return the detached flag
         */
        public boolean isDetached()
        {
            return detached;
        }

        /**
         * Another observer was added for this tracked node. This method returns
         * a new instance with an adjusted observer count.
         *
         * @return the updated instance
         */
        public TrackedNodeData observerAdded()
        {
            return new TrackedNodeData(node, observerCount + 1, isDetached());
        }

        /**
         * An observer for this tracked node was removed. This method returns a
         * new instance with an adjusted observer count. If there are no more
         * observers, result is <b>null</b>. This means that this node is no
         * longer tracked and can be released.
         *
         * @return the updated instance or <b>null</b>
         */
        public TrackedNodeData observerRemoved()
        {
            return (observerCount <= 1) ? null : new TrackedNodeData(node,
                    observerCount - 1, isDetached());
        }

        /**
         * Updates the node reference. This method is called after an update of
         * the underlying node structure if the tracked node was replaced by
         * another instance.
         *
         * @param newNode the new tracked node instance
         * @return the updated instance
         */
        public TrackedNodeData updateNode(ImmutableNode newNode)
        {
            return new TrackedNodeData(newNode, observerCount, isDetached());
        }

        /**
         * Returns an instance with the detached flag set to true. This method
         * is called if the selector of a tracked node does not match a single
         * node any more.
         *
         * @return the updated instance
         */
        public TrackedNodeData detach()
        {
            return new TrackedNodeData(getNode(), observerCount, true);
        }
    }
}
