package com.hmall.itemservice.es;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.itemservice.domain.po.Item;
import com.hmall.itemservice.domain.po.ItemDoc;
import com.hmall.itemservice.service.IItemService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: piggy
 * @CreateTime: 2025-04-04
 * @Description:
 * @Version: 1.0
 */

//@SpringBootTest(properties = "spring.profiles.active=local")
public class ElasticSearchTest {

    private RestHighLevelClient client;

    @BeforeEach
    void setUp() {
        this.client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.66.3:9200")
        ));
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void testSearch() throws IOException {
        // 1.创建查询对象
        SearchRequest request = new SearchRequest("items");

        // 2.参数填写
        request.source()
                .query(QueryBuilders.matchAllQuery());

        // 3.发送请求
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);

        System.out.println("searchResponse = " + searchResponse);

        // 4.解析结果
        parseSearchResponse(searchResponse);
    }

    private static void parseSearchResponse(SearchResponse searchResponse) {
        SearchHits searchHits = searchResponse.getHits();

        // 获取总条数
        long total = searchHits.getTotalHits().value;

        System.out.println("total = " + total);

        // 获取命中数据
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            // 转为字符转
            String json = hit.getSourceAsString();

            // 转为doc对象
            ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);

            // 处理高亮结果
            // 获取高亮结果
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if (CollUtil.isNotEmpty(highlightFields)) {
                // 获取高亮字段
                HighlightField name = highlightFields.get("name");

                // 获取高亮片段
                String replaceName = name.getFragments()[0].string();

                // 替换原有值
                itemDoc.setName(replaceName);
            }
            System.out.println("itemDoc = " + itemDoc);
        }
    }

    // 构建复合查询
    @Test
    void testCompoundSearch() throws IOException {
        // 创建查询对象
        SearchRequest request = new SearchRequest("items");

        // 参数填写
        request.source()
                .query(QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery("name", "脱脂牛奶"))
                        .filter(QueryBuilders.termQuery("brand", "德亚"))
                        .filter(QueryBuilders.rangeQuery("price").lte(30000)));

        // 发送请求
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);

        // 解析结果
        parseSearchResponse(searchResponse);
    }

    // 分页和排序
    @Test
    void testPageAndSort() throws IOException {
        int pageNum = 2, pageSize = 7;

        // 创建查询对象
        SearchRequest request = new SearchRequest("items");

        // 参数填写
        request.source()
                .query(QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery("name", "脱脂牛奶"))
                        .filter(QueryBuilders.termQuery("brand", "德亚"))
                        .filter(QueryBuilders.rangeQuery("price").lte(30000)))
                .from((pageNum - 1) * pageSize) // 起始页
                .size(pageSize) // 每页条数
                .sort("price", SortOrder.DESC) // 排序
                .sort("sold", SortOrder.ASC);

        // 发送请求
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);

        // 解析结果
        parseSearchResponse(searchResponse);
    }

    // 分页和排序
    @Test
    void testHighlight() throws IOException {
        int pageNum = 2, pageSize = 7;

        // 创建查询对象
        SearchRequest request = new SearchRequest("items");

        // 参数填写
        request.source().query(QueryBuilders.matchQuery("name", "脱脂牛奶"))
                .highlighter(SearchSourceBuilder.highlight()
                        .field("name")
                        .preTags("<em>") // 前缀标签, 前缀和后缀标签可以不带，默认就是<em>和</em>
                        .postTags("</em>"));

        // 发送请求
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);

        // 解析结果
        parseSearchResponse(searchResponse);
    }

    // 条件聚合
    @Test
    void testAggregation() throws IOException {
        String aggName = "brandAgg";
        // 创建请求对象
        SearchRequest request = new SearchRequest("items");

        // 填写DSL参数
        request.source()
                .aggregation(
                        AggregationBuilders
                                .terms(aggName)
                                .field("brand")
                                .size(10) // 聚合结果数量
                )
                .size(0); // 不需要查询数据，只需要聚合结果

        // 发送请求
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);

        // 解析结果
        // 获取聚合结果
        Aggregations aggregations = searchResponse.getAggregations();
        // 获取品牌聚合结果
        Terms brandAgg = aggregations.get(aggName);
        // 获取buckets
        List<? extends Terms.Bucket> buckets = brandAgg.getBuckets();
        // 遍历桶聚合
        buckets.forEach(bucket -> {
            // 获取key
            String key = bucket.getKeyAsString();

            // 获取品牌条目数量
            long docCount = bucket.getDocCount();

            System.out.println("key = " + key + ", docCount = " + docCount);
        });
    }
}
