package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.dto.AdminBrevoTemplateDto;
import com.grun.calorietracker.entity.NotificationCampaignEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MailProvider;
import com.grun.calorietracker.exception.MailDeliveryException;
import com.grun.calorietracker.service.BrevoCampaignEmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.*;

@Service @RequiredArgsConstructor
public class BrevoCampaignEmailServiceImpl implements BrevoCampaignEmailService {
    private static final Logger log = LoggerFactory.getLogger(BrevoCampaignEmailServiceImpl.class);
    private final RestClient.Builder restClientBuilder;
    private final MailProperties properties;

    @Override public List<AdminBrevoTemplateDto> templates(){
        try {
            requireBrevo();
            TemplateList response=restClientBuilder.build().get().uri(apiRoot()+"/smtp/templates?templateStatus=true&limit=1000")
                    .header("api-key",properties.getBrevo().getApiKey()).header("accept","application/json").retrieve().body(TemplateList.class);
            if(response==null||response.templates()==null)return List.of();
            return response.templates().stream().filter(TemplateItem::isActive).map(item->new AdminBrevoTemplateDto(item.id(),item.name(),item.subject(),item.isActive(),item.sender()==null?null:item.sender().email(),item.sender()==null?null:item.sender().name())).toList();
        } catch(RuntimeException ex){
            log.warn("Brevo campaign templates are unavailable: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override public String send(NotificationCampaignEntity campaign,UserEntity user){
        requireBrevo();if(campaign.getEmailTemplateId()==null||campaign.getEmailTemplateId()<=0)throw new IllegalArgumentException("Select an active Brevo template for email delivery.");
        Map<String,Object> params=new LinkedHashMap<>();params.put("name",user.getName()==null?"":user.getName());params.put("email",user.getEmail());params.put("campaignTitle",campaign.getTitle());params.put("message",campaign.getMessage());params.put("targetRoute",campaign.getTargetRoute()==null?"":campaign.getTargetRoute());
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("to",List.of(Map.of("email",user.getEmail(),"name",user.getName()==null?"":user.getName())));payload.put("templateId",campaign.getEmailTemplateId());payload.put("params",params);payload.put("tags",List.of("admin-campaign-"+campaign.getId(),"marketing"));
        try {SendResponse response=restClientBuilder.build().post().uri(apiRoot()+"/smtp/email").header("api-key",properties.getBrevo().getApiKey()).header("accept","application/json").header("Content-Type","application/json").body(payload).retrieve().body(SendResponse.class);return response==null?null:response.messageId();}
        catch(RestClientException ex){throw new MailDeliveryException("Brevo campaign email delivery failed",ex);}
    }
    private void requireBrevo(){if(properties.getProvider()!=MailProvider.BREVO||properties.getBrevo().getApiKey()==null||properties.getBrevo().getApiKey().isBlank())throw new IllegalStateException("Brevo provider and API key are required for email campaigns.");}
    private String apiRoot(){String url=properties.getBrevo().getApiUrl();int marker=url.indexOf("/smtp/");return marker>0?url.substring(0,marker):"https://api.brevo.com/v3";}
    @JsonIgnoreProperties(ignoreUnknown=true) record TemplateList(List<TemplateItem> templates){}
    @JsonIgnoreProperties(ignoreUnknown=true) record TemplateItem(long id,String name,String subject,boolean isActive,Sender sender){}
    @JsonIgnoreProperties(ignoreUnknown=true) record Sender(String email,String name){}
    @JsonIgnoreProperties(ignoreUnknown=true) record SendResponse(String messageId){}
}
