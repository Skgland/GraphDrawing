package de.webtwob.agd.s4.layouts;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.elk.core.math.KVectorChain;
import org.eclipse.elk.core.util.ElkUtil;
import org.eclipse.elk.graph.ElkConnectableShape;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;

import de.webtwob.agd.s4.layouts.options.LayerBasedMetaDataProvider;

public class Util {
    
    public static final int UNASSIGNED = -1;

    public static final Comparator<ElkNode> COMPARE_POS_IN_LAYER = Comparator
            .<ElkNode> comparingInt((ElkNode n) -> n.getProperty(LayerBasedMetaDataProvider.OUTPUTS_POS_IN_LAYER));

    /**
     * @param edge
     *            the edge to reverse
     * 
     *            This method expects a simple Edge and will swap source and target
     */
    public static void reverseEdge(ElkEdge edge) {
        replaceEnds(edge, getTarget(edge), getSource(edge));

        edge.setProperty(LayerBasedLayoutMetadata.OUTPUTS_EDGE_REVERSED,
                !edge.getProperty(LayerBasedLayoutMetadata.OUTPUTS_EDGE_REVERSED));
    }

    /**
     * Breaks up edges spanning more than one layer up, by inserting dummy nodes and edges
     */
    public static void breakUpEdge(ElkEdge origEdge) {

        final ElkNode origSource = Util.getSource(origEdge);
        final ElkNode origTarget = Util.getTarget(origEdge);

        final int sourceLayer = Util.getLayer(origSource);
        final int targetLayer = Util.getLayer(origTarget);

        if (targetLayer - sourceLayer <= 1) {
            // edge does not span multiple layers or wrong direction
            return;
        }

        ElkNode previous = origSource;
        ElkNode dummy = createDummyVersion(previous);
        dummy.setProperty(LayerBasedLayoutMetadata.OUTPUTS_IN_LAYER, sourceLayer + 1);
        dummy.setHeight(origEdge.getProperty(LayerBasedLayoutMetadata.EDGE_THICKNESS));

        // use origEdge as first new Edge
        replaceEnds(origEdge, origSource, dummy);

        previous = dummy;

        // add a node per intermediate layer
        for (int layer = sourceLayer + 2; layer < targetLayer; layer += 1) {

            // create dummy
            dummy = createDummyVersion(previous);
            dummy.setProperty(LayerBasedLayoutMetadata.OUTPUTS_IN_LAYER, layer);
            dummy.setHeight(origEdge.getProperty(LayerBasedLayoutMetadata.EDGE_THICKNESS));

            // create dummy edge between previous and dummy
            replaceEnds(createDummyVersion(origEdge), previous, dummy);

            // prepare for next iteration
            previous = dummy;
        }

        // create edge between last dummy and target
        replaceEnds(createDummyVersion(origEdge), dummy, origTarget);
    }

    /**
     * Undoes BreakUpEdge
     */
    public static void restoreBrokenEdge(ElkEdge origEdge) {
        if (origEdge.getProperty(LayerBasedLayoutMetadata.OUTPUTS_IS_DUMMY)) {
            // dummy edge is the wrong starting point
            return;
        }

        // Chain of points to route the edge over
        KVectorChain chain = new KVectorChain();

        final ElkNode source;
        final ElkNode target;
        final ElkEdge firstEdge;
        final ElkEdge lastEdge;

        {

            ElkNode next;
            ElkEdge currentDummyEdge = origEdge;

            if (Util.getSource(origEdge).getProperty(LayerBasedLayoutMetadata.OUTPUTS_IS_DUMMY)) {
                // edge had been reversed we need to start at the other end

                lastEdge = origEdge;
                next = Util.getSource(origEdge);
                target = Util.getTarget(origEdge);

                // add intermediate points
                while (next.getProperty(LayerBasedLayoutMetadata.OUTPUTS_IS_DUMMY)) {
                    chain.addFirst(next.getX(), next.getY()+next.getHeight()/2);
                    chain.addFirst(next.getX() + next.getWidth(), next.getY()+next.getHeight()/2);
                    
                    currentDummyEdge = next.getIncomingEdges().get(0);

                    next = Util.getSource(currentDummyEdge);
                }

                firstEdge = currentDummyEdge;
                source = next;

            } else {
                source = Util.getSource(origEdge);
                next = Util.getTarget(origEdge);
                firstEdge = origEdge;

                // add intermediate points
                while (next.getProperty(LayerBasedLayoutMetadata.OUTPUTS_IS_DUMMY)) {
                    chain.addLast(next.getX(), next.getY()+next.getHeight()/2);
                    chain.addLast(next.getX() + next.getWidth(), next.getY()+next.getHeight()/2);

                    currentDummyEdge = next.getOutgoingEdges().get(0);

                    next = Util.getTarget(currentDummyEdge);
                }

                lastEdge = currentDummyEdge;
                target = next;
            }
        }

        List<ElkEdgeSection> sections;
        sections = firstEdge.getSections();

        // add starting point
        if (sections.isEmpty()) {
            chain.addFirst(source.getX(), source.getY());
        } else {
            chain.addFirst(sections.get(0).getStartX(), sections.get(0).getStartY());
        }

        // add end point

        sections = lastEdge.getSections();

        if (sections.isEmpty()) {
            chain.addLast(target.getX(), target.getY());
        } else {
            chain.addLast(sections.get(sections.size() - 1).getEndX(), sections.get(sections.size() - 1).getEndY());
        }

        // update original Edge
        replaceEnds(origEdge, source, target);
        ElkUtil.applyVectorChain(chain, ElkGraphUtil.firstEdgeSection(origEdge, false, true));
    }

    /**
     * Assumes Simple Edges
     * 
     * Returns true iff the source with the same parent of all incoming edges have an assigned layer
     */
    public static boolean allPredecessorsHaveAnAssignedLayer(ElkNode n) {
        return n.getIncomingEdges().stream().map(Util::getSource).map(ElkGraphUtil::connectableShapeToNode)
                .noneMatch(s -> isLayerAssigned(s) && (n.getParent().equals(s.getParent())));
    }

    /**
     * @return true if s has been assigned to a Layer
     */
    public static boolean isLayerAssigned(ElkNode s) {
        return s.getProperty(LayerBasedLayoutMetadata.OUTPUTS_IN_LAYER) == LayerBasedLayoutMetadata.OUTPUTS_IN_LAYER
                .getDefault();
    }

    /**
     * Assumes Layers to be Assigned
     * 
     * Returns a Map of Layers
     */

    public static Map<Integer, List<ElkNode>> getLayers(ElkNode graph) {
        return graph.getChildren().stream().collect(
                Collectors.groupingBy((ElkNode n) -> n.getProperty(LayerBasedMetaDataProvider.OUTPUTS_IN_LAYER)));
    }

    /**
     * Assumes Layers to be Assigned
     * 
     * Returns the Layer of the Node
     */

    public static int getLayer(ElkNode node) {
        return node.getProperty(LayerBasedMetaDataProvider.OUTPUTS_IN_LAYER);
    }

    /**
     * A version of ElkGraphUtil.getTargetNode which doesn't throw if more than one Target is present
     */
    public static ElkNode getTarget(ElkEdge edge) {
        if (edge.getTargets().size() < 1) {
            throw new IllegalArgumentException("Passed Egde does not have any Targets!");
        }

        return ElkGraphUtil.connectableShapeToNode(edge.getTargets().get(0));

    }

    /**
     * A version of ElkGraphUtil.getSourceNode which doesn't throw if more than one Target is present
     */
    public static ElkNode getSource(ElkEdge edge) {
        if (edge.getSources().size() < 1) {
            throw new IllegalArgumentException("Passed Egde does not have any Sources!");
        }

        return ElkGraphUtil.connectableShapeToNode(edge.getSources().get(0));

    }

    public static boolean isSimpleEdge(ElkEdge edge) {
        return edge.getTargets().size() == 1 && edge.getSources().size() == 1;
    }

    public static ElkNode createDummyVersion(ElkNode node) {
        ElkNode nwe = ElkGraphUtil.createNode(node.getParent());
        nwe.copyProperties(node);
        nwe.setProperty(LayerBasedMetaDataProvider.OUTPUTS_IS_DUMMY, true);
        return nwe;
    }

    public static ElkEdge createDummyVersion(ElkEdge edge) {
        ElkEdge nwe = ElkGraphUtil.createEdge(edge.getContainingNode());
        nwe.copyProperties(edge);
        nwe.setProperty(LayerBasedMetaDataProvider.OUTPUTS_IS_DUMMY, true);
        return nwe;
    }

    public static void replaceEnds(ElkEdge edge, ElkConnectableShape start, ElkConnectableShape end) {

        edge.getSources().clear();
        edge.getTargets().clear();
        edge.getSources().add(start);
        edge.getTargets().add(end);
    }

}
