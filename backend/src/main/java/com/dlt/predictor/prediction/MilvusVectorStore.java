package com.dlt.predictor.prediction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * Milvus SDK 适配器，同时维护联合向量和前后区分区向量。
 *
 * <p>Milvus 只保存 PostgreSQL 主键与号码向量；开奖详情始终从 PostgreSQL 读取。</p>
 *
 */
@Service
class MilvusVectorStore implements VectorStore {

    private static final int JOINT_DIMENSIONS = 47;
    private static final int FRONT_DIMENSIONS = 35;
    private static final int BACK_DIMENSIONS = 12;
    private static final String ID_FIELD = "draw_id";
    private static final String JOINT_FIELD = "vector";
    private static final String FRONT_FIELD = "front_vector";
    private static final String BACK_FIELD = "back_vector";

    private final MilvusClientV2 client;
    private final MilvusProperties properties;
    private boolean jointInitialized;
    private boolean transitionInitialized;

    MilvusVectorStore(MilvusClientV2 client, MilvusProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void upsert(long drawId, float[] jointVector, float[] frontVector, float[] backVector) {
        validateVector(jointVector, JOINT_DIMENSIONS);
        validateVector(frontVector, FRONT_DIMENSIONS);
        validateVector(backVector, BACK_DIMENSIONS);
        try {
            ensureJointCollection();
            JsonObject jointRow = row(drawId);
            jointRow.add(JOINT_FIELD, values(jointVector));
            client.upsert(UpsertReq.builder()
                    .collectionName(properties.collectionName())
                    .data(List.of(jointRow))
                    .build());

            ensureTransitionCollection();
            JsonObject transitionRow = row(drawId);
            transitionRow.add(FRONT_FIELD, values(frontVector));
            transitionRow.add(BACK_FIELD, values(backVector));
            client.upsert(UpsertReq.builder()
                    .collectionName(properties.transitionCollectionName())
                    .data(List.of(transitionRow))
                    .build());
        } catch (RuntimeException exception) {
            throw new VectorStoreException("Milvus multi-vector upsert failed", exception);
        }
    }

    @Override
    public List<VectorSearchHit> searchJoint(float[] vector, int limit) {
        validateVector(vector, JOINT_DIMENSIONS);
        ensureJointCollection();
        return search(properties.collectionName(), JOINT_FIELD, vector, limit);
    }

    @Override
    public List<VectorSearchHit> searchFront(float[] vector, int limit) {
        validateVector(vector, FRONT_DIMENSIONS);
        ensureTransitionCollection();
        return search(properties.transitionCollectionName(), FRONT_FIELD, vector, limit);
    }

    @Override
    public List<VectorSearchHit> searchBack(float[] vector, int limit) {
        validateVector(vector, BACK_DIMENSIONS);
        ensureTransitionCollection();
        return search(properties.transitionCollectionName(), BACK_FIELD, vector, limit);
    }

    private List<VectorSearchHit> search(String collection, String field, float[] vector, int limit) {
        try {
            SearchResp response = client.search(SearchReq.builder()
                    .collectionName(collection)
                    .annsField(field)
                    .data(List.of(new FloatVec(vector)))
                    .topK(limit)
                    .build());
            return response.getSearchResults().stream()
                    .flatMap(List::stream)
                    .map(hit -> new VectorSearchHit(((Number) hit.getId()).longValue(), hit.getScore()))
                    .toList();
        } catch (RuntimeException exception) {
            throw new VectorStoreException("Milvus search failed", exception);
        }
    }

    private synchronized void ensureJointCollection() {
        if (jointInitialized) {
            return;
        }
        if (!hasCollection(properties.collectionName())) {
            CreateCollectionReq.CollectionSchema schema = baseSchema();
            schema.addField(vectorField(JOINT_FIELD, JOINT_DIMENSIONS));
            createCollection(properties.collectionName(), schema, List.of(index(JOINT_FIELD)));
        }
        load(properties.collectionName());
        jointInitialized = true;
    }

    private synchronized void ensureTransitionCollection() {
        if (transitionInitialized) {
            return;
        }
        if (!hasCollection(properties.transitionCollectionName())) {
            CreateCollectionReq.CollectionSchema schema = baseSchema();
            schema.addField(vectorField(FRONT_FIELD, FRONT_DIMENSIONS));
            schema.addField(vectorField(BACK_FIELD, BACK_DIMENSIONS));
            createCollection(
                    properties.transitionCollectionName(),
                    schema,
                    List.of(index(FRONT_FIELD), index(BACK_FIELD)));
        }
        load(properties.transitionCollectionName());
        transitionInitialized = true;
    }

    private CreateCollectionReq.CollectionSchema baseSchema() {
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName(ID_FIELD)
                .dataType(DataType.Int64)
                .isPrimaryKey(true)
                .autoID(false)
                .build());
        return schema;
    }

    private static AddFieldReq vectorField(String name, int dimensions) {
        return AddFieldReq.builder()
                .fieldName(name)
                .dataType(DataType.FloatVector)
                .dimension(dimensions)
                .build();
    }

    private static IndexParam index(String field) {
        return IndexParam.builder()
                .fieldName(field)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.IP)
                .build();
    }

    private boolean hasCollection(String collection) {
        return Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder()
                .collectionName(collection)
                .build()));
    }

    private void createCollection(
            String collection,
            CreateCollectionReq.CollectionSchema schema,
            List<IndexParam> indexes) {
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collection)
                .collectionSchema(schema)
                .indexParams(indexes)
                .build());
    }

    private void load(String collection) {
        client.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
    }

    private static JsonObject row(long drawId) {
        JsonObject row = new JsonObject();
        row.addProperty(ID_FIELD, drawId);
        return row;
    }

    private static JsonArray values(float[] vector) {
        JsonArray values = new JsonArray();
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private static void validateVector(float[] vector, int dimensions) {
        if (vector == null || vector.length != dimensions) {
            throw new IllegalArgumentException("Expected a " + dimensions + "-dimensional vector");
        }
    }
}
