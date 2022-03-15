package com.zhiyiyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhiyiyo.constants.SystemConstants;
import com.zhiyiyo.domain.ResponseResult;
import com.zhiyiyo.domain.dto.ArticleDTO;
import com.zhiyiyo.domain.entity.ArticleTag;
import com.zhiyiyo.domain.entity.Category;
import com.zhiyiyo.domain.entity.Tag;
import com.zhiyiyo.domain.vo.*;
import com.zhiyiyo.enums.AppHttpCodeEnum;
import com.zhiyiyo.mapper.ArticleMapper;
import com.zhiyiyo.domain.entity.Article;
import com.zhiyiyo.service.ArticleService;
import com.zhiyiyo.service.ArticleTagService;
import com.zhiyiyo.service.CategoryService;
import com.zhiyiyo.service.TagService;
import com.zhiyiyo.utils.Assert;
import com.zhiyiyo.utils.BeanCopyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章表(Article)表服务实现类
 */
@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ArticleTagService articleTagService;

    @Autowired
    private TagService tagService;

    @Override
    public List<Article> listNormalArticle() {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, SystemConstants.ARTICLE_STATUS_NORMAL);
        return list(wrapper);
    }

    @Override
    public ResponseResult getHotArticleList() {
        // 查询出非草稿、没有被删除的文章，并按照热度降序排序前 5 文章
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, SystemConstants.ARTICLE_STATUS_NORMAL);
        wrapper.orderByDesc(Article::getViewCount);
        // wrapper.last("limit 5");

        Page<Article> page = new Page<>(1, 5);
        this.page(page, wrapper);

        List<Article> records = page.getRecords();
        return ResponseResult.okResult(BeanCopyUtils.copyBeanList(records, HotArticleVo.class));
    }

    @Override
    public ResponseResult getArticleList(Integer pageNum, Integer pageSize, Long categoryId, Long tagId) {
        // 构造查询条件
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, SystemConstants.ARTICLE_STATUS_NORMAL);
        wrapper.orderByDesc(Article::getIsTop);
        wrapper.eq(categoryId != null, Article::getCategoryId, categoryId);
        if (tagId != null) {
            LambdaQueryWrapper<ArticleTag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.eq(ArticleTag::getTagId, tagId);
            List<ArticleTag> articleTags = articleTagService.list(tagWrapper);
            wrapper.in(Article::getId, articleTags.stream().map(ArticleTag::getArticleId).collect(Collectors.toList()));
        }

        // 从数据库中分页查询
        Page<Article> page = new Page<>(pageNum, pageSize);
        this.page(page, wrapper);
        List<Article> articles = page.getRecords();

        // 设置文章分类名
        for (Article article : articles) {
            String categoryName = categoryService.getById(article.getCategoryId()).getName();
            article.setCategoryName(categoryName);
        }

        List<ArticleListVo> articleListVos = BeanCopyUtils.copyBeanList(articles, ArticleListVo.class);
        return ResponseResult.okResult(new PageVo<>(page.getTotal(), articleListVos));
    }

    @Override
    public ResponseResult getArticleDetail(Long id) {
        // 从数据库中查询文章
        Article article = getById(id);
        Assert.notNull(article, AppHttpCodeEnum.RESOURCE_NOT_EXIST);
        ArticleDetailsVo articleDetailsVO = BeanCopyUtils.copyBean(article, ArticleDetailsVo.class);

        // 设置分类名称
        Category category = categoryService.getById(article.getCategoryId());
        if (category != null) {
            articleDetailsVO.setCategoryName(category.getName());
        }

        // 设置标签
        LambdaQueryWrapper<ArticleTag> articleTagWrapper = new LambdaQueryWrapper<>();
        articleTagWrapper.eq(ArticleTag::getArticleId, id);
        List<ArticleTag> articleTags = articleTagService.list(articleTagWrapper);
        List<Long> tagIds = articleTags.stream().map(ArticleTag::getTagId).collect(Collectors.toList());

        if (tagIds.size() > 0) {
            LambdaQueryWrapper<Tag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.in(Tag::getId, tagIds);
            List<Tag> tags = tagService.list(tagWrapper);
            articleDetailsVO.setTags(BeanCopyUtils.copyBeanList(tags, TagVo.class));
        }

        return ResponseResult.okResult(articleDetailsVO);
    }

    @Override
    public ResponseResult getArticleCount() {
        long article = count();
        long category = categoryService.count();
        Long tag = tagService.count();
        return ResponseResult.okResult(new ArticleCountVo(article, category, tag));
    }

    @Override
    public ResponseResult updateViewCount(Long id) {
        Article article = getById(id);
        Assert.notNull(article, AppHttpCodeEnum.RESOURCE_NOT_EXIST);
        article.setViewCount(article.getViewCount() + 1);
        updateById(article);
        return ResponseResult.okResult();
    }

    @Override
    public ResponseResult getPreviousNextArticle(Long id) {
        // 查询当前的文章
        Article article = getById(id);
        Assert.notNull(article, AppHttpCodeEnum.RESOURCE_NOT_EXIST);
        PreviousNextArticleVo previousNextArticleVo = new PreviousNextArticleVo();

        // 查询上一篇文章
        QueryWrapper<Article> previousWrapper = new QueryWrapper<>();
        previousWrapper.lt("create_time", article.getCreateTime());
        previousWrapper.orderByDesc("create_time").last("limit 1");
        Article previousArticle = getOne(previousWrapper);
        if (previousArticle != null) {
            previousNextArticleVo.setPrevious(BeanCopyUtils.copyBean(previousArticle, HotArticleVo.class));
        }

        // 查询下一篇文章
        QueryWrapper<Article> nextWrapper = new QueryWrapper<>();
        nextWrapper.gt("create_time", article.getCreateTime());
        nextWrapper.orderByAsc("create_time").last("limit 1");
        Article nextArticle = getOne(nextWrapper);
        if (nextArticle != null) {
            previousNextArticleVo.setNext(BeanCopyUtils.copyBean(nextArticle, HotArticleVo.class));
        }

        return ResponseResult.okResult(previousNextArticleVo);
    }

    @Override
    public ResponseResult addArticle(ArticleDTO article) {
        Article newArticle = BeanCopyUtils.copyBean(article, Article.class);

        // 设置分类 id
        Category category = categoryService.getOrAddCategoryByName(article.getCategory());
        newArticle.setCategoryId(category.getId());

        // 设置文章状态
        String status = article.getIsDraft() ? SystemConstants.ARTICLE_STATUS_Draft : SystemConstants.ARTICLE_STATUS_NORMAL;
        newArticle.setStatus(status);
        saveOrUpdate(newArticle);

        // 设置标签
        List<ArticleTag> articleTags = article.getTags().stream()
                .map(name -> new ArticleTag(newArticle.getId(), tagService.getOrAddTagByName(name).getId()))
                .collect(Collectors.toList());
        articleTagService.saveBatch(articleTags);

        return ResponseResult.okResult(newArticle.getId());
    }

    @Override
    public ResponseResult editArticle(ArticleDTO article) {
        // 移除文章的旧标签
        LambdaQueryWrapper<ArticleTag> articleTagWrapper = new LambdaQueryWrapper<>();
        articleTagWrapper.eq(ArticleTag::getArticleId, article.getId());
        articleTagService.remove(articleTagWrapper);

        return addArticle(article);
    }
}

