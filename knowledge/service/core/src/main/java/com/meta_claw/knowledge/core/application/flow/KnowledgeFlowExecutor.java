package com.meta_claw.knowledge.core.application.flow;

import com.meta_claw.knowledge.core.application.flow.register.BuildSourceRecordNode;
import com.meta_claw.knowledge.core.application.flow.register.BuildSourceRegistrationResultNode;
import com.meta_claw.knowledge.core.application.flow.register.CreateSnapshotNode;
import com.meta_claw.knowledge.core.application.flow.register.DecideUnchangedNode;
import com.meta_claw.knowledge.core.application.flow.register.LoadLatestSnapshotNode;
import com.meta_claw.knowledge.core.application.flow.register.PersistChangedSourceAndSnapshotNode;
import com.meta_claw.knowledge.core.application.flow.register.PersistUnchangedSourceNode;
import com.meta_claw.knowledge.core.application.flow.register.ResolveKnowledgeSpaceNode;
import com.meta_claw.knowledge.core.application.flow.resume.BuildResumeResultNode;
import com.meta_claw.knowledge.core.application.flow.resume.CreateNextSnapshotNode;
import com.meta_claw.knowledge.core.application.flow.resume.DecideResumeNeededNode;
import com.meta_claw.knowledge.core.application.flow.resume.LoadLatestSnapshotForResumeNode;
import com.meta_claw.knowledge.core.application.flow.resume.LoadSourceForResumeNode;
import com.meta_claw.knowledge.core.application.flow.resume.PersistResumedSnapshotNode;
import com.meta_claw.knowledge.core.application.flow.worker.BuildIngestWorkerResultNode;
import com.meta_claw.knowledge.core.application.flow.worker.BuildSubmitWorkerJobResultNode;
import com.meta_claw.knowledge.core.application.flow.worker.BuildWorkerJobNode;
import com.meta_claw.knowledge.core.application.flow.worker.IngestKnowledgeStateNode;
import com.meta_claw.knowledge.core.application.flow.worker.ReadWorkerResultEnvelopeNode;
import com.meta_claw.knowledge.core.application.flow.worker.ResolveWorkerKnowledgeSpaceNode;
import com.meta_claw.knowledge.core.application.flow.worker.SubmitWorkerJobNode;
import com.yomahub.liteflow.builder.LiteFlowNodeBuilder;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.property.LiteflowConfig;

/**
 * LiteFlow 最小执行器封装。
 * 负责初始化当前 core 模块使用的 chain 资源，并向上层暴露单例执行器。
 */
public class KnowledgeFlowExecutor {

    private final FlowExecutor flowExecutor;

    public KnowledgeFlowExecutor() {
        registerNodes();
        LiteflowConfig liteflowConfig = new LiteflowConfig();
        liteflowConfig.setRuleSource("liteflow/register-resume-el.xml");
        this.flowExecutor = new FlowExecutor(liteflowConfig);
        this.flowExecutor.init(true);
    }

    public FlowExecutor getFlowExecutor() {
        return flowExecutor;
    }

    private void registerNodes() {
        registerNode("resolveKnowledgeSpaceNode", ResolveKnowledgeSpaceNode.class);
        registerNode("buildSourceRecordNode", BuildSourceRecordNode.class);
        registerNode("loadLatestSnapshotNode", LoadLatestSnapshotNode.class);
        registerNode("createSnapshotNode", CreateSnapshotNode.class);
        registerNode("decideUnchangedNode", DecideUnchangedNode.class);
        registerNode("persistChangedSourceAndSnapshotNode", PersistChangedSourceAndSnapshotNode.class);
        registerNode("persistUnchangedSourceNode", PersistUnchangedSourceNode.class);
        registerNode("buildSourceRegistrationResultNode", BuildSourceRegistrationResultNode.class);

        registerNode("loadSourceForResumeNode", LoadSourceForResumeNode.class);
        registerNode("loadLatestSnapshotForResumeNode", LoadLatestSnapshotForResumeNode.class);
        registerNode("decideResumeNeededNode", DecideResumeNeededNode.class);
        registerNode("createNextSnapshotNode", CreateNextSnapshotNode.class);
        registerNode("persistResumedSnapshotNode", PersistResumedSnapshotNode.class);
        registerNode("buildResumeResultNode", BuildResumeResultNode.class);

        registerNode("resolveWorkerKnowledgeSpaceNode", ResolveWorkerKnowledgeSpaceNode.class);
        registerNode("buildWorkerJobNode", BuildWorkerJobNode.class);
        registerNode("submitWorkerJobNode", SubmitWorkerJobNode.class);
        registerNode("buildSubmitWorkerJobResultNode", BuildSubmitWorkerJobResultNode.class);
        registerNode("readWorkerResultEnvelopeNode", ReadWorkerResultEnvelopeNode.class);
        registerNode("ingestKnowledgeStateNode", IngestKnowledgeStateNode.class);
        registerNode("buildIngestWorkerResultNode", BuildIngestWorkerResultNode.class);
    }

    private void registerNode(String nodeId, Class<?> nodeClass) {
        LiteFlowNodeBuilder.createCommonNode()
                .setId(nodeId)
                .setName(nodeId)
                .setClazz(nodeClass)
                .build();
    }
}
