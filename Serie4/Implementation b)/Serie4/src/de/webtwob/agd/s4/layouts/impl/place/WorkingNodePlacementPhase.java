package de.webtwob.agd.s4.layouts.impl.place;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.elk.core.alg.ILayoutPhase;
import org.eclipse.elk.core.alg.LayoutProcessorConfiguration;
import org.eclipse.elk.core.util.IElkProgressMonitor;
import org.eclipse.elk.graph.ElkNode;

import de.webtwob.agd.s4.layouts.Util;
import de.webtwob.agd.s4.layouts.enums.LayoutPhasesEnum;
import de.webtwob.agd.s4.layouts.enums.ProcessorEnum;
import de.webtwob.agd.s4.layouts.options.LayerBasedMetaDataProvider;

public class WorkingNodePlacementPhase implements ILayoutPhase<LayoutPhasesEnum, ElkNode> {

    //This does not accommodate for dummy's being on the same y-Level!
    //First NodePlacementPhase implementation just to get it running
    
    @Override
    public void process(ElkNode graph, IElkProgressMonitor progressMonitor) {
        progressMonitor.begin("WorkingNodePlacementPhase", graph.getChildren().size());

        Map<Integer, List<ElkNode>> layers = Util.getLayers(graph);
        
        double maxX = 0;
        double maxY = 0;
        double maxWidth = 0;
        
        for(int i = 0 ;i<graph.getProperty(LayerBasedMetaDataProvider.OUTPUTS_LAYER_COUNT);i++) {
            if (progressMonitor.isCanceled()) {
                progressMonitor.done();
                return;
            }
            maxY = 0;
            maxWidth = 0;
            List<ElkNode> layer = layers.getOrDefault(i, Collections.<ElkNode>emptyList());
            layer.sort(Util.COMPARE_POS_IN_LAYER);
            for(ElkNode node: layer) {
                if (progressMonitor.isCanceled()) {
                    progressMonitor.done();
                    return;
                }
                node.setX(maxX);
                node.setY(maxY);
                
                maxY += node.getHeight() +20; //TODO make the margin an option
                maxWidth = Math.max(maxWidth, node.getWidth());
                progressMonitor.worked(1);
            }
            
            maxX += maxWidth + 20; //TODO make the margin an option
            
        }
        
        progressMonitor.done();
        
    }

    @Override
    public LayoutProcessorConfiguration<LayoutPhasesEnum, ElkNode> getLayoutProcessorConfiguration(ElkNode graph) {
        return LayoutProcessorConfiguration.<LayoutPhasesEnum, ElkNode>create()
                .before(LayoutPhasesEnum.CROSSING_MINIMIZATION)
                .add(ProcessorEnum.DUMMY_PLACEMENT)
                .after(LayoutPhasesEnum.EDGE_ROUTING)
                .add(ProcessorEnum.UNDO_DUMMY_PLACEMENT);
    }

}
