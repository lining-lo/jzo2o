package com.jzo2o.foundations.service.impl;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.common.utils.StringUtils;
import com.jzo2o.es.core.ElasticSearchTemplate;
import com.jzo2o.foundations.constants.IndexConstants;
import com.jzo2o.foundations.model.domain.ServeAggregation;
import com.jzo2o.foundations.model.dto.response.ServeSimpleResDTO;
import com.jzo2o.foundations.service.ServeAggregationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ServeAggegationServiceImpl implements ServeAggregationService {

    @Autowired
    private ElasticSearchTemplate elasticSearchTemplate;

    /**
     * 服务搜索
     *
     * @param cityCode
     * @param serveTypeId
     * @param keyword
     * @return
     */
    @Override
    public List<ServeSimpleResDTO> search(String cityCode, Long serveTypeId, String keyword) {

        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder();
        //1. 设置索引库名
        searchRequestBuilder.index(IndexConstants.SERVE);

        //2. 设置查询条件： 布尔组合查询
        searchRequestBuilder.query(query->query.bool(bool->{

            //必须的查询条件：根据城市编码term查询
            bool.must(must-> must.term(term->term.field("city_code").value(cityCode)));

            //可能的筛选条件：根据查询关键词multi_match查询
            if(StringUtils.isNotBlank(keyword)){
                bool.filter(filter->filter.multiMatch(multi->multi.fields("serve_item_name","serve_type_name").query(keyword)));
            }
            
            //可能的筛选条件：根据服务类型id精确查询
            if(serveTypeId!=null) {
                bool.filter(filter->filter.term(term->term.field("serve_type_id").value(serveTypeId)));
            }

            //返回bool对象
            return bool;
        }));

        //3. 设置排序：根据序号升序
        searchRequestBuilder.sort(x->x.field(y->y.field("serve_item_sort_num").order(SortOrder.Asc)));

        //4. 执行查询
        SearchResponse<ServeAggregation> searchResponse = elasticSearchTemplate.opsForDoc().search(searchRequestBuilder.build(), ServeAggregation.class);

        //5. 解析响应结果
        List<Hit<ServeAggregation>> hitList = searchResponse.hits().hits();

        List<ServeSimpleResDTO> resultList = new ArrayList<>();
        if(CollUtils.isNotEmpty(hitList)){
            for (Hit<ServeAggregation> hit : hitList) {
                ServeAggregation aggregation = hit.source();
                ServeSimpleResDTO dto = BeanUtils.copyBean(aggregation, ServeSimpleResDTO.class);
                resultList.add(dto);
            }
        }
        return resultList;
    }
}