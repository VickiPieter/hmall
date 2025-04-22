package com.hmall.searchservice.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmall.api.dto.ItemDTO;
import com.hmall.searchservice.domain.po.ItemDoc;
import com.hmall.searchservice.domain.query.ItemPageQuery;
import com.hmall.searchservice.service.IItemService;
import com.hmall.common.domain.PageDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHost;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.web.bind.annotation.*;

import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "搜索相关接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(
            HttpHost.create("http://192.168.66.3:9200")
    ));
    private final IItemService itemService;

    @ApiOperation("根据id查找商品")
    @GetMapping("/{id}")
    public ItemDTO findById(@PathVariable Long id) throws IOException {
        // 准备请求对象
        GetRequest item = new GetRequest("items").id(id.toString());
        // 查找数据
        GetResponse response = restHighLevelClient.get(item, RequestOptions.DEFAULT);
        // 解析数据
        String source = response.getSourceAsString();
        // 反序列化数据，并返回
        return BeanUtil.copyProperties(JSONUtil.toBean(source, ItemDoc.class), ItemDTO.class);
    }

    @ApiOperation("搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDoc> search(ItemPageQuery query) throws IOException {
        // 1. 创建请求对象
        SearchRequest request = new SearchRequest("items");
        // 2. 构建复合条件查询
        BoolQueryBuilder queryBuilder = getBoolQueryBuilder(query);

        // 3.构建DSL参数
        request.source().query(QueryBuilders.functionScoreQuery(
                        // 基础查询条件
                        queryBuilder,
                        // 将广告商品的权重提升10倍
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                        QueryBuilders.termQuery("isAD", true),
                                        ScoreFunctionBuilders.weightFactorFunction(10)
                                )
                        }
                ).boostMode(CombineFunction.MULTIPLY))
                .from(query.from())
                .size(query.getPageSize());

        // 4.排序
        if (StrUtil.isNotBlank(query.getSortBy())){
            request.source().sort(query.getSortBy(), query.getIsAsc() ? SortOrder.ASC : SortOrder.DESC);
        }

        // 5.发送请求
        SearchResponse searchResponse = restHighLevelClient.search(request, RequestOptions.DEFAULT);
        // 6.解析结果
        SearchHits searchHits = searchResponse.getHits();
        // 7.获取总记录数
        long total = searchHits.getTotalHits().value;
        // 当前页码
        long pageNo = Long.valueOf(query.getPageNo());
        // 8.获取命中数据
        SearchHit[] hits = searchHits.getHits();
        // 9.遍历数据
        List<ItemDoc> itemDocs = new ArrayList<>();
        for (SearchHit hit : hits) {
            // 转为字符串对象
            String json = hit.getSourceAsString();
            // 转为doc对象
            itemDocs.add(JSONUtil.toBean(json, ItemDoc.class));
        }
        return PageDTO.of(total, pageNo, query.getPageSize().longValue(), itemDocs);
    }

    @ApiOperation("过滤条件")
    @PostMapping("/filters")
    public Map filter(@RequestBody ItemPageQuery query) throws IOException {
        // 创建请求对象
        SearchRequest request = new SearchRequest("items");
        BoolQueryBuilder queryBuilder = getBoolQueryBuilder(query);
        // 构建DSL参数
        request.source()
                .query(queryBuilder)
                .size(0)
                .aggregation(
                        AggregationBuilders.terms("categoryAgg")
                                .field("category")
                                .size(10)
                )
                .aggregation(
                        AggregationBuilders.terms("brandAgg")
                                .field("brand")
                                .size(10)
                );

        // 发送请求
        SearchResponse searchResponse = restHighLevelClient.search(request, RequestOptions.DEFAULT);
        // 解析结果
        Aggregations aggregations = searchResponse.getAggregations();
        // 获取分类聚合
        Terms categoryAgg = aggregations.get("categoryAgg");
        Terms brandAgg = aggregations.get("brandAgg");
        // 获取分类聚合结果
        List<? extends Terms.Bucket> categoryAggBuckets = categoryAgg.getBuckets();
        List<? extends Terms.Bucket> brandAggBuckets = brandAgg.getBuckets();
        // 遍历获取每一个bucket
        List<String> categoryList = new ArrayList<>();
        List<String> brandList = new ArrayList<>();
        Map<String, List<String>> bucket = new HashMap<>();
        categoryAggBuckets.forEach(categoryBucket -> {
            String key = categoryBucket.getKeyAsString();
            categoryList.add(key);
        });
        bucket.put("category", categoryList);
        brandAggBuckets.forEach(brandBucket -> {
            String key = brandBucket.getKeyAsString();
            brandList.add(key);
        });
        bucket.put("brand", brandList);
        return bucket;
    }

    private static BoolQueryBuilder getBoolQueryBuilder(ItemPageQuery query) {
        // 2. 构建复合条件查询
        BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
        if (StrUtil.isNotBlank(query.getKey())){
            // 2.1 模糊查询
            queryBuilder.must(matchQuery("name", query.getKey()));
        }
        if (StrUtil.isNotBlank(query.getCategory())){
            // 2.2 分类查询
            queryBuilder.filter(QueryBuilders.termQuery("category", query.getCategory()));
        }
        if (StrUtil.isNotBlank(query.getBrand())){
            // 2.3 品牌查询
            queryBuilder.filter(QueryBuilders.termQuery("brand", query.getBrand()));
        }
        if (query.getMaxPrice() != null){
            // 2.4 价格区间查询
            queryBuilder.filter(QueryBuilders.rangeQuery("price").lte(query.getMaxPrice()));
        }
        if (query.getMinPrice() != null){
            // 2.5 价格区间查询
            queryBuilder.filter(QueryBuilders.rangeQuery("price").gte(query.getMinPrice()));
        }
        return queryBuilder;
    }
}