package com.hyang.ich.content;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.dto.IchCategoryDTO;
import com.hyang.ich.content.dto.IchHeritageManDTO;
import com.hyang.ich.content.dto.IchItemDTO;

import java.util.List;

public interface ContentService {

    // ========== 非遗分类 ==========

    /** 查询所有分类（树形结构） */
    List<IchCategoryDTO> listCategoryTree();

    /** 查询分类列表 */
    List<IchCategoryDTO> listCategories(Long parentId);

    /** 根据ID查询分类 */
    IchCategoryDTO getCategoryById(Long id);

    /** 新增分类 */
    IchCategoryDTO addCategory(IchCategoryDTO categoryDTO);

    /** 修改分类 */
    void updateCategory(IchCategoryDTO categoryDTO);

    /** 删除分类 */
    void deleteCategory(Long id);

    // ========== 非遗项目 ==========

    /** 分页查询非遗项目 */
    PageResult<IchItemDTO> listItems(int pageNum, int pageSize, Long categoryId, String keyword, Integer status);

    /** 根据ID查询非遗项目 */
    IchItemDTO getItemById(Long id);

    /** 新增非遗项目 */
    IchItemDTO addItem(IchItemDTO itemDTO);

    /** 修改非遗项目 */
    void updateItem(IchItemDTO itemDTO);

    /** 删除非遗项目 */
    void deleteItem(Long id);

    /** 更新项目状态（上架/下架） */
    void updateItemStatus(Long id, Integer status);

    // ========== 传承人 ==========

    /** 分页查询传承人 */
    PageResult<IchHeritageManDTO> listHeritageMan(int pageNum, int pageSize, String keyword, Integer status);

    /** 根据ID查询传承人 */
    IchHeritageManDTO getHeritageManById(Long id);

    /** 新增传承人 */
    IchHeritageManDTO addHeritageMan(IchHeritageManDTO heritageManDTO);

    /** 修改传承人 */
    void updateHeritageMan(IchHeritageManDTO heritageManDTO);

    /** 删除传承人 */
    void deleteHeritageMan(Long id);
}
