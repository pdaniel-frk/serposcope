/* 
 * Serposcope - SEO rank checker https://serposcope.serphacker.com/
 * 
 * Copyright (c) 2016 SERP Hacker
 * @author Pierre Nogues <support@serphacker.com>
 * @license https://opensource.org/licenses/MIT MIT License
 */
package serposcope.controllers.admin;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.db.google.GoogleDB;
import com.serphacker.serposcope.models.base.Config;
import com.serphacker.serposcope.models.base.Config.CaptchaService;
import static com.serphacker.serposcope.models.base.Config.PATTERN_CRONTIME;
import com.serphacker.serposcope.models.google.GoogleSettings;
import com.serphacker.serposcope.scraper.captcha.solver.AntiCaptchaSolver;
import com.serphacker.serposcope.scraper.captcha.solver.CaptchaSolver;
import com.serphacker.serposcope.scraper.captcha.solver.DeathByCaptchaSolver;
import com.serphacker.serposcope.scraper.captcha.solver.DecaptcherSolver;
import com.serphacker.serposcope.scraper.google.GoogleDevice;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ninja.Context;
import ninja.FilterWith;
import ninja.Result;
import ninja.Results;
import ninja.Router;
import ninja.i18n.Messages;
import ninja.params.Param;
import ninja.session.FlashScope;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import serposcope.controllers.BaseController;
import serposcope.controllers.HomeController;
import serposcope.filters.AdminFilter;
import serposcope.filters.XSRFFilter;
import serposcope.helpers.Validator;

@FilterWith(AdminFilter.class)
@Singleton
public class SettingsController extends BaseController {
    
    private static final DateTimeFormatter HOUR_MINUTES = DateTimeFormatter.ofPattern("HH:mm");
        
    private static final Logger LOG = LoggerFactory.getLogger(SettingsController.class);
    
    @Inject
    BaseDB baseDB;
    
    @Inject
    Router router;
    
    @Inject
    Messages msg;
    
    public Result settings(){
        return Results
            .ok()
            .render("serverTime", LocalTime.now().format(HOUR_MINUTES))
            .render("config", baseDB.config.getConfig());
    }
    
    @FilterWith(XSRFFilter.class)
    public Result update(
        Context context,
        @Param("displayHome") String displayHome,
        @Param("displayGoogleTarget") String displayGoogleTarget,
        @Param("displayGoogleSearch") String displayGoogleSearch,
        @Param("cronTime") String cronTime,
        @Param("service") String captchaService,
        @Param("captchaUser") String captchaUser,
        @Param("captchaPass") String captchaPass,
        @Param("captchaApiKey") String captchaApiKey
    ){
        FlashScope flash = context.getFlashScope();
        
        Config config = new Config();

        Config.CaptchaService service = Config.CaptchaService.fromString(captchaService);
        if(Config.CaptchaService.DISABLE.equals(service)){
            captchaUser = null;
            captchaPass = null;
        }
        
        if(cronTime == null || cronTime.isEmpty()){
            config.setCronTime("");
        } else {
            try {
                config.setCronTime(LocalTime.parse(cronTime));
            } catch(Exception ex){
                flash.error("admin.settings.cronTimeError");
                return Results.redirect(router.getReverseRoute(SettingsController.class, "settings"));                
            }
//            Matcher matcher = PATTERN_CRONTIME.matcher(cronTime);
//            if(!matcher.find()){
//                flash.error("admin.settings.cronTimeError");
//                return Results.redirect(router.getReverseRoute(SettingsController.class, "settings"));
//            }
//            config.setCronTime(LocalTime.of(Integer.parseInt(matcher.group(0)), Integer.parseInt(matcher.group(1))));
        }
            
        config.setCaptchaService(service);
        switch(service){
            case DEATHBYCAPTCHA:
            case DECAPTCHER:
                config.setDbcUser(captchaUser);
                config.setDbcPass(captchaPass);
                break;
            case ANTICAPTCHA:
                config.setDbcApi(captchaApiKey);
                break;
        }
        
        if(displayHome != null && !Config.DEFAULT_DISPLAY_HOME.equals(displayHome) && Config.VALID_DISPLAY_HOME.contains(displayHome)){
            config.setDisplayHome(displayHome);
        }
        
        if(displayGoogleTarget != null && !Config.DEFAULT_DISPLAY_GOOGLE_TARGET.equals(displayGoogleTarget) 
            && Config.VALID_DISPLAY_GOOGLE_TARGET.contains(displayGoogleTarget)){
            config.setDisplayGoogleTarget(displayGoogleTarget);
        }
        
        if(displayGoogleSearch != null && !Config.DEFAULT_DISPLAY_GOOGLE_SEARCH.equals(displayGoogleSearch) 
            && Config.VALID_DISPLAY_GOOGLE_SEARCH.contains(displayGoogleSearch)){
            config.setDisplayGoogleSearch(displayGoogleSearch);
        }        
        
        baseDB.config.updateConfig(config);
        
        flash.success("label.settingsUpdated");
        return Results.redirect(router.getReverseRoute(SettingsController.class, "settings"));
    }
    
    @FilterWith(XSRFFilter.class)
    public Result reset(
        Context context
    ){
        baseDB.config.updateConfig(new Config());
        context.getFlashScope().success("label.settingsUpdated");
        return Results.redirect(router.getReverseRoute(SettingsController.class, "settings"));
    }
    
    public Result testCaptcha(
        Context context,
        @Param("service") String captchaService,
        @Param("user") String captchaUser,
        @Param("pass") String captchaPass,
        @Param("api") String captchaApiKey
    ){
        
        CaptchaService service = CaptchaService.fromString(captchaService);
        
        CaptchaSolver solver = null;
        switch(service){
            case DEATHBYCAPTCHA:
                if(!StringUtils.isEmpty(captchaUser) && !StringUtils.isEmpty(captchaPass)){
                    solver = new DeathByCaptchaSolver(captchaUser, captchaPass);
                }
                break;
            case DECAPTCHER:
                if(!StringUtils.isEmpty(captchaUser) && !StringUtils.isEmpty(captchaPass)){
                    solver = new DecaptcherSolver(captchaUser, captchaPass);
                }
                break;
            case ANTICAPTCHA:
                if(!StringUtils.isEmpty(captchaApiKey)){
                    solver = new AntiCaptchaSolver(captchaApiKey);
                }
                break;
        }
        
        if(solver == null){
            return Results.ok().text().render(msg.get("admin.settings.invalidService", context, Optional.absent()).get());
        }
        
        try {
            if(!solver.init()){
                return Results.ok().text().render(msg.get("admin.settings.failedInitService", context, Optional.absent(), solver.getFriendlyName()).get());
            }

            if(!solver.testLogin()){
                return Results.ok().text().render(msg.get("admin.settings.invalidServiceCredentials", context, Optional.absent(), solver.getFriendlyName()).get());
            }

            float balance = solver.getCredit();
            return Results.ok().text().render("OK, balance = " + balance);
        }finally{
            try{solver.close();}catch(Exception ex){}
        }
    }
}
