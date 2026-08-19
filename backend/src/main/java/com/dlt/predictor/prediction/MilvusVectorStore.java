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
 * Milvus SDK 适配器。
 *
 * <p>Milvus 只保存 PostgreSQL 主键与 47 维号码向量；开奖详情始终从 PostgreSQL 读取。</p>
 *
 */
@Service
class MilvusVectorStore implements VectorStore {

    private static final int VECTOR_DIMENSIONS = 47;
    private static final String ID_FIELD = "draw_id";
    private static final String VECTOR_FIELD = "vector";

    private final MilvusClientV2 client;
    private final MilvusProperties properties;
    private boolean initialized;

    MilvusVectorStore(MilvusClientV2 client, MilvusProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void upsert(long drawId, float[] vector) {
        validateVector(vector);
        try {
            ensureCollection();
            // 以 PostgreSQL draw_id 作为固定主键，使重复同步变成幂等覆盖。
            JsonObject row = new JsonObject();
            row.addProperty(ID_FIELD, drawId);
            JsonArray values = new JsonArray();
            for (float value : vector) {
                values.add(value);
            }
            row.add(VECTOR_FIELD, values);
            client.upsert(UpsertReq.builder()
                    .collectionName(properties.collectionName())
                    .data(List.of(row))
                    .build());
        } catch (RuntimeException exception) {
            throw new VectorStoreException("Milvus upsert failed", exception);
        }
    }

    @Override
    public List<VectorSearchHit> search(float[] vector, int limit) {
        validateVector(vector);
        try {
            ensureCollection();
            SearchResp response = client.search(SearchReq.builder()
                    .collectionName(properties.collectionName())
                    .annsField(VECTOR_FIELD)
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

    private synchronized void ensureCollection() {
        // 首次读写时初始化 collection，synchronized 防止并发请求重复创建。
        if (initialized) {
            return;
        }
        if (!Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder()
                .collectionName(properties.collectionName())
                .build()))) {
            // 明确 schema 和 IP 索引，保证检索分数可直接表示向量内积。
            CreateCollectionReq.CollectionSchema schema = client.createSchema();
            schema.addField(AddFieldReq.builder()
                    .fieldName(ID_FIELD)
                    .dataType(DataType.Int64)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(VECTOR_FIELD)
                    .dataType(DataType.FloatVector)
                    .dimension(VECTOR_DIMENSIONS)
                    .build());
            client.createCollection(CreateCollectionReq.builder()
                    .collectionName(properties.collectionName())
                    .collectionSchema(schema)
                    .indexParams(List.of(IndexParam.builder()
                            .fieldName(VECTOR_FIELD)
                            .indexType(IndexParam.IndexType.AUTOINDEX)
                            .metricType(IndexParam.MetricType.IP)
                            .build()))
                    .build());
        }
        client.loadCollection(LoadCollectionReq.builder().collectionName(properties.collectionName()).build());
        initialized = true;
    }

    private static void validateVector(float[] vector) {
        if (vector == null || vector.length != VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException("Expected a 47-dimensional vector");
        }
    }
}
