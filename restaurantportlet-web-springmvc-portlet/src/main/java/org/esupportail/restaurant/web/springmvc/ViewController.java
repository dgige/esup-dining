package org.esupportail.restaurant.web.springmvc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.esupportail.restaurant.domain.beans.User;
import org.esupportail.restaurant.services.auth.Authenticator;
import org.esupportail.restaurant.web.dao.DatabaseConnector;
import org.esupportail.restaurant.web.flux.RestaurantFlux;
import org.esupportail.restaurant.web.json.Manus;
import org.esupportail.restaurant.web.json.Restaurant;
import org.esupportail.restaurant.web.json.RestaurantFeedRoot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.portlet.ModelAndView;

@Controller
@RequestMapping("VIEW")
public class ViewController extends AbstractExceptionController {

	@Autowired
	private Authenticator authenticator;
	@Autowired
	private DatabaseConnector dc;
	@Autowired
	private RestaurantFlux flux;
	private RestaurantFeedRoot restaurants;
	
	@RequestMapping
    public ModelAndView renderMainView(RenderRequest request, RenderResponse response) throws Exception {	
    	
    	ModelMap model = new ModelMap();
    	
    	User user = authenticator.getUser();
    	String areaToDisplay = new String();

    	try {
    		ResultSet results = dc.executeQuery("SELECT AREANAME FROM USERAREA WHERE USERNAME='"+user.getLogin()+"';");
    		results.next();
    		areaToDisplay = results.getString("AREANAME");
    	} catch (SQLException e) {
    		// we are here if the user doesn't set a specific area for himself
    		ResultSet results = dc.executeQuery("SELECT AREANAME FROM PATHFLUX");
        	results.next();
        	try {
        		areaToDisplay = results.getString("AREANAME");
        	} catch (SQLException e2) {
        		// If there is no default area, then the admin must configure the portlet before.
        		model.put("nothingToDisplay", "This portlet needs to be configured by an authorized user");
        	}
    	}
    	
    	
    	try {
    		ResultSet favList = dc.executeQuery("SELECT RESTAURANTID FROM FAVORITERESTAURANT WHERE USERNAME='" + user.getLogin() +"';");
    		List<Restaurant> favorites = new ArrayList<Restaurant>();
    		
    		if(favList.next()) {
	    		for(Restaurant r : flux.getFlux().getRestaurants()) {
	    			
	    			do {
	    				if(r.getId() == favList.getInt("RESTAURANTID"))
	    					favorites.add(r);
	    			} while(favList.next());
	    			
	    			favList.first();
	    		}
	    		
	    		model.put("favorites", favorites);
    		}
    		
    	} catch(SQLException e) {
    		// Nothing to do here.
    	} catch(NullPointerException e2) {
    		// nop
    	}
    	

    	model.put("area", areaToDisplay);
    	
    	try {
    		
    		restaurants = flux.getFlux();
    		
        	List<Restaurant> dininghallList = new ArrayList<Restaurant>();   	
        	
    		for(Restaurant restaurant : restaurants.getRestaurants()) {
    			if(restaurant.getArea().equalsIgnoreCase(areaToDisplay))
    				dininghallList.add(restaurant);
    		}
 			model.put("dininghalls", dininghallList);
   
    	} catch(Exception e) {
    		model.put("nothingToDisplay", "This portlet needs to be configured by an authorized user");
    	}
    	return new ModelAndView("view", model);
    }
    
    
    
    @RequestMapping(params = {"action=viewRestaurant"})
    public ModelAndView renderRestaurantView(RenderRequest request, RenderResponse response, @RequestParam(value = "id", required = true) int id) throws Exception {
    	
    	ModelMap model = new ModelMap();
    	
    	User user = authenticator.getUser();
    	
    	try {
    		restaurants = flux.getFlux();
    		for(Restaurant r : restaurants.getRestaurants()) {
    			if(r.getId() == id) {
    				model.put("restaurant", r);

    				ResultSet results = dc.executeQuery("SELECT * FROM FAVORITERESTAURANT WHERE USERNAME='" + user.getLogin() +"' AND RESTAURANTID='" + id + "';");
     				
    				if(results.next()) {
    					 model.put("isFavorite", true);
    				}
    			}
    		}
    	
    	} catch(NullPointerException e) {
    		model.put("nothingToDisplay", "This portlet needs to be configured by an authorized user");
    	}

    	return new ModelAndView("restaurant", model);
    }
    
    @RequestMapping(params = {"action=viewMeals"})
    public ModelAndView renderMealsView(RenderRequest request, RenderResponse response, @RequestParam(value = "id", required=true) int id) throws Exception {
    	ModelMap model = new ModelMap();
    	
    	try {
    		restaurants = flux.getFlux();
    		for(Restaurant r : restaurants.getRestaurants()) {
    			if(r.getId() == id) {
    				model.put("restaurant", r);
    				
    				List<Manus> menuList = new ArrayList<Manus>();
    				Date dateNow = new Date();
    				for(Manus m : r.getMenus())  {
    					Date dateMenu = new SimpleDateFormat("yyyy-MM-dd").parse(m.getDate());
    					// We only send upcomings menu to the view
    					if(dateMenu.compareTo(dateNow) >= 0) {
    						menuList.add(m);
    					}
    				}
    				model.put("menus", menuList);	
    			}
    		}
    	
    	} catch(Exception e) {
    		model.put("nothingToDisplay", "This portlet needs to be configured by an authorized user");
    	}
    	
    	return new ModelAndView("meals", model);
    }
    
    @RequestMapping(params = {"action=setFavorite"})
    public void setFavorite(ActionRequest request, ActionResponse response, @RequestParam(value = "id", required = true) String id) throws Exception {
    	
    	User user = authenticator.getUser();
    	
    	try {
    		dc.executeUpdate("INSERT INTO FAVORITERESTAURANT VALUES ('" + user.getLogin() + "', '" + id + "');");
    	} catch (SQLException e) {
    		e.printStackTrace();
    	}
    	
    	response.setRenderParameter("id", id);
    	response.setRenderParameter("action", "viewRestaurant");    	
    }    
   
    @RequestMapping(params = {"action=viewDish"})
    public ModelAndView renderDish(RenderRequest request, RenderResponse response, 
			   @RequestParam(value = "name", required = true) String name,
			   @RequestParam(value = "ingredients", required = false) String ingredients,
			   @RequestParam(value = "nutritionitems", required = false) String nutritionitems,
			   @RequestParam(value = "code", required = false) String code,
			   @RequestParam(value = "id", required=true) int id) throws Exception {
    	
    	
    	ModelMap model = new ModelMap();
    	
    	model.put("restaurantId", id);
    	model.put("name", name);
    	model.put("ingredients", ingredients);
    	model.put("code", code.substring(1, code.length()-1).split(","));
    	
    	/* Awful code starts now */
    	// Need to find an other solution... a cleaner one.
    	
    	String str = nutritionitems.substring(1, nutritionitems.length()-1);		
		List<Map<String, String>> listNutritionItems = new ArrayList<Map<String, String>>();
		Pattern p = Pattern.compile("\\[(.*?)\\]");
		Matcher m = p.matcher(str);
		while(m.find()) {
			Map<String, String> entry = new HashMap<String, String>();
			for(String s2 : m.group(1).split(",")) {
				String[] keyValue = s2.split("=");
				entry.put(keyValue[0], keyValue[1]);
			}
			listNutritionItems.add(entry);
		}
			
		/* Awful code ends now */
    	
    	model.put("nutritionitems", listNutritionItems);
    	
    	return new ModelAndView("dish", model);
    }	
	
}
