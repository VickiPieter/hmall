package com.hmall.searchservice.listener;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.hmall.api.clients.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.searchservice.constants.MQConstant;
import com.hmall.searchservice.domain.po.ItemDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;

/**
 * @Author: piggy
 * @CreateTime: 2025-04-06
 * @Description: 监听来自ItemController发来的CURD
 * @Version: 1.0
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemListener {

    private final ItemClient itemClient;

    private final RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(
            HttpHost.create("http://192.168.66.3:9200")
    ));

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MQConstant.INDEX_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(value = MQConstant.SEARCH_DIRECT_NAME),
            key = MQConstant.INDEX_QUEUE_KEY
    ))
    public void listenIndex(Long id) throws IOException {
        log.info("order id is {}", id);
        // 从数据库查找数据
        ItemDTO itemDTO = itemClient.queryItemById(id);
        if (itemDTO == null) return;
        // 将DTO转换为Doc
        ItemDoc itemDoc = BeanUtil.copyProperties(itemDTO, ItemDoc.class);
        // 请求资源
        IndexRequest source = new IndexRequest("items")
                .source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
        // 发送请求
        restHighLevelClient.index(source, RequestOptions.DEFAULT);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MQConstant.DELETE_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(value = MQConstant.SEARCH_DIRECT_NAME),
            key = MQConstant.DELETE_QUEUE_KEY
    ))
    public void listenDelete(@PathVariable("id") Long id) throws IOException {
        log.info("order id is {}", id);
        // 准备请求对象
        DeleteRequest item = new DeleteRequest("items").id(id.toString());
        // 发送请求
        restHighLevelClient.delete(item, RequestOptions.DEFAULT);
    }

    /**
     * 这里不采用局部修改，代码过多；采用全量修改，简洁
     * @param id 商品id
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MQConstant.UPDATE_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(value = MQConstant.SEARCH_DIRECT_NAME),
            key = MQConstant.UPDATE_QUEUE_KEY
    ))
    public void listenUpdate(Long id) throws IOException {
        log.info("order id is {}", id);
        // 从数据库查找该条数据
        ItemDTO itemDTO = itemClient.queryItemById(id);
        if (itemDTO == null) return;
        ItemDoc itemDoc = BeanUtil.copyProperties(itemDTO, ItemDoc.class);
        IndexRequest items = new IndexRequest("items")
                .id(itemDoc.getId())
                .source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
        restHighLevelClient.index(items, RequestOptions.DEFAULT);
    }
}
