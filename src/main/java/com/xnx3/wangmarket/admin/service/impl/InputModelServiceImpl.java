package com.xnx3.wangmarket.admin.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import com.xnx3.file.FileUtil;
import com.xnx3.j2ee.Global;
import com.xnx3.j2ee.dao.SqlDAO;
import com.xnx3.j2ee.vo.BaseVO;
import com.xnx3.wangmarket.admin.Func;
import com.xnx3.wangmarket.admin.cache.Template;
import com.xnx3.wangmarket.admin.entity.InputModel;
import com.xnx3.wangmarket.admin.entity.News;
import com.xnx3.wangmarket.admin.entity.SiteColumn;
import com.xnx3.wangmarket.admin.service.InputModelService;
import com.xnx3.wangmarket.admin.util.AliyunLog;
import com.xnx3.wangmarket.admin.vo.bean.NewsInit;

@Service("InputModelService")
public class InputModelServiceImpl implements InputModelService {
	//默认的系统输入模型，只加载一次。位于应用根目录 static/inputModel/default.html
	private static String defaultInputModelText = null;	

	@Resource
	private SqlDAO sqlDAO;

	public String getInputModelText(int modelId) {
		Map<Integer, InputModel> map = getInputModelBySession();
		
		InputModel inputModel = map.get(modelId);
		if(inputModel == null){
			return null;
		}else{
			return inputModel.getText();
		}
	}

	public List<InputModel> getInputModelListForSession() {
		Map<Integer, InputModel> map = getInputModelBySession();
		
		List<InputModel> inputModelList = new ArrayList<InputModel>();
		for (InputModel model : map.values()) {  
			inputModelList.add(model);
		}
		return inputModelList;
	}

	/**
	 * 获取当前session中的输入模型。若没有，则从数据库中加载当前网站的输入模型数据到Session中。
	 */
	public Map<Integer, InputModel> getInputModelBySession(){
		Map<Integer, InputModel> map = Func.getUserBeanForShiroSession().getInputModelMap();
		
		//若是第一次使用，需要从数据库加载输入模型数据
		if(map == null){
			map = new HashMap<Integer, InputModel>();
			
			int siteid = Func.getUserBeanForShiroSession().getSite().getId();
			List<InputModel> inputModelList = sqlDAO.findBySqlQuery("SELECT * FROM input_model WHERE siteid = " + siteid, InputModel.class);
			if(inputModelList != null && inputModelList.size() > 0){
				//如果取到了当前网站有自己的输入模型，那么将其加入session缓存中
				for (int i = 0; i < inputModelList.size(); i++) {
					InputModel model = inputModelList.get(i);
					map.put(model.getId(), model);
				}
				Func.getUserBeanForShiroSession().setInputModelMap(map);
			}
		}
		
		return map;
	}

	public InputModel getInputModelById(int modelId) {
		return getInputModelBySession().get(modelId);
	}

	public BaseVO saveInputModel(InputModel inputModel) {
		BaseVO vo = new BaseVO();
		
		sqlDAO.save(inputModel);
		if(inputModel.getId() != null && inputModel.getId() > 0){
			//数据库的保存成功，那么更新Session缓存的
			Map<Integer, InputModel> map = getInputModelBySession();
			map.put(inputModel.getId(), inputModel);
			Func.getUserBeanForShiroSession().setInputModelMap(map);
			
			vo.setInfo(inputModel.getId()+"");
			return vo;
		}else{
			vo.setBaseVO(BaseVO.FAILURE, "保存失败");
			return vo;
		}
	}

	public BaseVO removeInputModel(int inputModelId) {
		BaseVO vo = new BaseVO();
		
		//判断要删除的这个输入模型是否是该用户的
		InputModel inputModel = sqlDAO.findById(InputModel.class, inputModelId);
		if(inputModel == null){
			vo.setBaseVO(BaseVO.FAILURE, "要删除的输入模型不存在");
			return vo;
		}
		if(inputModel.getSiteid() - Func.getCurrentSite().getId() != 0){
			vo.setBaseVO(BaseVO.FAILURE, "要删除的输入模型不属于您，删除失败");
			return vo;
		}
		
		sqlDAO.delete(inputModel);
		
		//数据库的删除了，那么也要删除掉Session缓存中的
		Map<Integer, InputModel> map = getInputModelBySession();
		map.remove(inputModelId);
		Func.getUserBeanForShiroSession().setInputModelMap(map);
		
		AliyunLog.addActionLog(inputModel.getSiteid(), "删除输入模型:" + inputModel.getRemark());
		
		return vo;
	}

	public String getInputModelTextByIdForNews(NewsInit newsInit) {
		SiteColumn siteColumn = newsInit.getSiteColumn();
		
		InputModel im = getInputModelByCodeName(siteColumn.getInputModelCodeName());
		String text = null;	//输入模型中获取的自定义模型具体内容
		
		//如果该栏目没有输入模型，那么用默认的
		if(im == null){
			text = getDefaultInputModelText();
		}else{
			//该栏目有输入模型，那么用自定义的输入模型
			text = im.getText();
		}
		
		if(text == null){
			return "出错！获取输入模型失败！请重新尝试";
		}else{
			//将输入模型进行动态数据的替换
			text = text.replaceAll(Template.regex("siteColumn.type"), siteColumn.getType()+"");
			
			News news = newsInit.getNews();
			if(news == null || news.getId() == null){
				text = text.replaceAll(Template.regex("news.title"), "");
				text = text.replaceAll(Template.regex("titlepicImage"), "");
				text = text.replaceAll(Template.regex("text"), "");
				text = text.replaceAll(Template.regex("news.intro"), "");
			}else{
				text = text.replaceAll(Template.regex("news.title"), news.getTitle());
				text = text.replaceAll(Template.regex("titlepicImage"), newsInit.getTitlepicImage());
				text = text.replaceAll(Template.regex("news.intro"), news.getIntro());
				//此处因replaceAll容易出问题，而且｛text｝也只会出现一次，所以直接换为了replace
				text = text.replace("{text}", newsInit.getNewsText());
			}
		}
		return text;
	}

	public InputModel getInputModelByCodeName(String codeName) {
		Map<Integer, InputModel> map = getInputModelBySession();
		for (Integer key : map.keySet()) {
		   InputModel inputModel = map.get(key);
		   if(inputModel != null && inputModel.getCodeName() != null && inputModel.getCodeName().equals(codeName)){
			   return inputModel;
		   }
		}
		return null;
	}

	public String getDefaultInputModelText() {
		if(defaultInputModelText == null){	
			defaultInputModelText = FileUtil.read(Global.getProjectPath()+"static/inputModel/default.html");
		}
		return defaultInputModelText;
	}
}
