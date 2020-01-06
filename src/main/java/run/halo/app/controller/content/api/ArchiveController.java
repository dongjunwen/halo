package run.halo.app.controller.content.api;

import com.alibaba.fastjson.JSON;
import org.json.JSONObject;
import org.springframework.data.domain.*;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.*;
import run.halo.app.cache.StringCacheStore;
import run.halo.app.exception.ForbiddenException;
import run.halo.app.model.entity.Category;
import run.halo.app.model.entity.Post;
import run.halo.app.model.entity.Tag;
import run.halo.app.model.enums.PostStatus;
import run.halo.app.model.vo.ArchiveMonthVO;
import run.halo.app.model.vo.ArchiveYearVO;
import run.halo.app.model.vo.BaseCommentVO;
import run.halo.app.model.vo.PostListVO;
import run.halo.app.service.*;
import run.halo.app.utils.MarkdownUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.data.domain.Sort.Direction.DESC;

/**
 * Archive portal controller.
 *
 * @author johnniang
 * @date 4/2/19
 */
@RestController("ApiContentArchiveController")
@RequestMapping("/api/content/archives")
public class ArchiveController {

    private final PostService postService;

    private final ThemeService themeService;

    private final PostCategoryService postCategoryService;

    private final PostTagService postTagService;

    private final PostCommentService postCommentService;

    private final OptionService optionService;

    private final StringCacheStore cacheStore;

    public ArchiveController(PostService postService, ThemeService themeService, PostCategoryService postCategoryService, PostTagService postTagService, PostCommentService postCommentService, OptionService optionService, StringCacheStore cacheStore) {
        this.postService = postService;
        this.themeService = themeService;
        this.postCategoryService = postCategoryService;
        this.postTagService = postTagService;
        this.postCommentService = postCommentService;
        this.optionService = optionService;
        this.cacheStore = cacheStore;
    }

    @GetMapping("years")
    public List<ArchiveYearVO> listYearArchives() {
        return postService.listYearArchives();
    }

    @GetMapping("months")
    public List<ArchiveMonthVO> listMonthArchives() {
        return postService.listMonthArchives();
    }


    @GetMapping(value = "findPage/{page}",produces = "application/json; charset=utf-8")
    public String findAarchivesPage(@PathVariable(value = "page") Integer page,
                                    @SortDefault(sort = "createTime", direction = DESC) Sort sort) {
        Pageable pageable = PageRequest.of(page - 1, optionService.getPostPageSize(), sort);

        Page<Post> postPage = postService.pageBy(PostStatus.PUBLISHED, pageable);
        Page<PostListVO> postListVos = postService.convertToListVo(postPage);
        Page pageResult = new PageImpl(postListVos.getContent(),pageable,postPage.getTotalElements());
        //int[] pageRainbow = PageUtil.rainbow(page, postListVos.getTotalPages(), 3);

      /*  model.addAttribute("is_archives", true);
        model.addAttribute("pageRainbow", pageRainbow);
        model.addAttribute("posts", postListVos);*/
        String retStr= JSON.toJSONString(pageResult);
        System.err.println(retStr);
        return retStr;
    }

    /**
     * Render post page.
     *
     * @param url     post slug url.
     * @param preview preview
     * @param token   preview token
     * @return template path: themes/{theme}/post.ftl
     */
    @GetMapping(value = "findByUrl/{url}",produces = "application/json; charset=utf-8")
    public String findByUrlId(@PathVariable("url") String url,
                              @RequestParam(value = "preview", required = false, defaultValue = "false") boolean preview,
                              @RequestParam(value = "intimate", required = false, defaultValue = "false") boolean intimate,
                              @RequestParam(value = "token", required = false) String token,
                              @RequestParam(value = "cp", defaultValue = "1") Integer cp,
                              @SortDefault(sort = "createTime", direction = DESC) Sort sort) {
        Post post;
        if (preview) {
            post = postService.getBy(PostStatus.DRAFT, url);
        } else if (intimate) {
            post = postService.getBy(PostStatus.INTIMATE, url);
        } else {
            post = postService.getBy(PostStatus.PUBLISHED, url);
        }

        // if this is a preview url.
        if (preview) {
            // render markdown to html when preview post
            post.setFormatContent(MarkdownUtils.renderHtml(post.getOriginalContent()));

            // verify token
            String cachedToken = cacheStore.getAny("preview-post-token-" + post.getId(), String.class).orElseThrow(() -> new ForbiddenException("该文章的预览链接不存在或已过期"));

            if (!cachedToken.equals(token)) {
                throw new ForbiddenException("该文章的预览链接不存在或已过期");
            }
        }

        // if this is a intimate url.
        if (intimate) {
            // verify token
            String cachedToken = cacheStore.getAny(token, String.class).orElseThrow(() -> new ForbiddenException("您没有该文章的访问权限"));
            if (!cachedToken.equals(token)) {
                throw new ForbiddenException("您没有该文章的访问权限");
            }
        }
/*
        postService.getNextPost(post.getCreateTime()).ifPresent(nextPost -> model.addAttribute("nextPost", nextPost));
        postService.getPrePost(post.getCreateTime()).ifPresent(prePost -> model.addAttribute("prePost", prePost));*/

        List<Category> categories = postCategoryService.listCategoriesBy(post.getId());
        List<Tag> tags = postTagService.listTagsBy(post.getId());

        Page<BaseCommentVO> comments = postCommentService.pageVosBy(post.getId(), PageRequest.of(cp, optionService.getCommentPageSize(), sort));
        JSONObject jsonObject=new JSONObject();


        jsonObject.put("is_post", true);
        jsonObject.put("articles", JSONObject.valueToString(postService.convertToDetailVo(post)));
        jsonObject.put("categories", categories);
        jsonObject.put("tags", tags);
        jsonObject.put("comments", comments);

        if (preview) {
            // refresh timeUnit
            cacheStore.putAny("preview-post-token-" + post.getId(), token, 10, TimeUnit.MINUTES);
        }

        return JSONObject.valueToString(postService.convertToDetailVo(post).getFormatContent());
    }
}
