package com.drajer.bsa.controller;

import static org.mockito.Mockito.mock;

import com.drajer.bsa.dao.HealthcareSettingsDao;
import com.drajer.bsa.model.HealthcareSetting;
import com.drajer.bsa.scheduler.ScheduleJobConfiguration;
import com.drajer.ecrapp.util.ApplicationUtils;
import com.drajer.test.WireMockQuery;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.File;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = ScheduleJobConfiguration.class)
public class ITSubscriptionNotificationReceiverControllerTest extends WireMockQuery {

  private Logger logger =
      LoggerFactory.getLogger(ITSubscriptionNotificationReceiverControllerTest.class);

  @Autowired SubscriptionNotificationReceiverController notificationController;

  @Autowired ApplicationUtils ap;

  @Autowired HealthcareSettingsDao hsDao;

  @Value("${test.diabetes.kar.directory}")
  String karDirectory;

  private ClassLoader classLoader = getClass().getClassLoader();

  @Test
  public void getNotificationContextDiabetesNumerCMS122_2Test() {
    Bundle bund =
        getNotificationBundle(
            "Bsa/Diabetes/numer-CMS122-2-Patient/numer-CMS122-2-notification-bundle.json");

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    setupHealthCareSettings();
    mockAccessToken();
    mockBasicEhrDataScenario("Diabetes", "numer-CMS122-2");

    notificationController.processNotification(
        getFhirParser().encodeResourceToString(bund), request, response);
  }

  @Test
  public void getNotificationContextDiabetesDenomCMS122_3Test() {
    Bundle bund =
        getNotificationBundle(
            "Bsa/Diabetes/denom-3-CMS122-Patient/denom-3-CMS122-notification-bundle.json");

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    setupHealthCareSettings();
    mockAccessToken();
    mockBasicEhrDataScenario("Diabetes", "denom-3-CMS122");

    notificationController.processNotification(
        getFhirParser().encodeResourceToString(bund), request, response);
  }

  @Test
  public void getNotificationContextDiabetesDenomCMS122Test() {
    Bundle bund =
        getNotificationBundle(
            "Bsa/Diabetes/denom-CMS122-Patient/denom-CMS122-notification-bundle.json");

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    setupHealthCareSettings();
    mockAccessToken();
    mockBasicEhrDataScenario("Diabetes", "denom-CMS122");

    notificationController.processNotification(
        getFhirParser().encodeResourceToString(bund), request, response);
  }

  @Test
  public void getNotificationContextDiabetesDenomExclCMS122Test() {
    Bundle bund =
        getNotificationBundle(
            "Bsa/Diabetes/denomexcl-CMS122-Patient/denomexcl-CMS122-notification-bundle.json");

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    setupHealthCareSettings();
    mockAccessToken();
    // mockBasicEhrDataScenario("Diabetes", "denomexcl-CMS122");

    notificationController.processNotification(
        getFhirParser().encodeResourceToString(bund), request, response);
  }

  @Test
  public void getNotificationContextDiabetesNumerCMS122Test() {
    Bundle bund =
        getNotificationBundle(
            "Bsa/Diabetes/numer-CMS122-Patient/numer-CMS122-notification-bundle.json");

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    setupHealthCareSettings();
    mockAccessToken();
    mockBasicEhrDataScenario("Diabetes", "numer-CMS122");

    notificationController.processNotification(
        getFhirParser().encodeResourceToString(bund), request, response);
  }

  private void mockBasicEhrDataScenario(String contentName, String scenario) {
    String conditionResourcePath =
        String.format("Bsa/%s/%s-Patient/%s-Condition.json", contentName, scenario, scenario);
    String conditionQueryString = String.format("/fhir/Condition/%s-Condition", scenario, scenario);
    mockResourceQuery(conditionResourcePath, conditionQueryString);
    String encounterResourcePath =
        String.format("Bsa/%s/%s-Patient/%s-Encounter.json", contentName, scenario, scenario);
    String encounterQueryString = String.format("/fhir/Encounter/%s-Encounter", scenario, scenario);
    mockResourceQuery(encounterResourcePath, encounterQueryString);
    String observationResourcePath =
        String.format("Bsa/%s/%s-Patient/%s-Observation.json", contentName, scenario, scenario);
    String observationQueryString =
        String.format("/fhir/Observation/%s-Observation", scenario, scenario);
    mockResourceQuery(observationResourcePath, observationQueryString);
    String patientResourcePath =
        String.format("Bsa/%s/%s-Patient/%s-Patient.json", contentName, scenario, scenario);
    String patientQueryString = String.format("/fhir/Patient/%s-Patient", scenario, scenario);
    mockResourceQuery(patientResourcePath, patientQueryString);
  }

  private void mockResourceQuery(String resourcePath, String mockQueryString) {
    File resourceFile = new File(classLoader.getResource(resourcePath).getFile());
    String resourceAbsolutePath = resourceFile.getAbsolutePath();
    IBaseResource resourceBase = ap.readResourceFromFile(resourceAbsolutePath);
    if (resourceBase == null || !(resourceBase instanceof Resource)) {
      logger.debug("Resource not found.");
    }
    Resource resource = (Resource) resourceBase;
    mockFhirRead(mockQueryString, resource);
  }

  private void setupHealthCareSettings() {
    String healthCareSettings = "Bsa/HealthCareSettings.json";
    File healthCareSettingsFile = new File(classLoader.getResource(healthCareSettings).getFile());
    HealthcareSetting hcs = null;
    try {
      hcs = mapper.readValue(healthCareSettingsFile, HealthcareSetting.class);
    } catch (JsonParseException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (JsonMappingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    if (hcs == null) {
      logger.debug("Health care settings not found: " + healthCareSettings);
    }

    hsDao.saveOrUpdate(hcs);
  }

  private void mockAccessToken() {
    String accessToken = "cb81ec9fa7d7605a060ffc756fc7d130";
    String expireTime = "3600";
    mockTokenResponse(
        "/token",
        String.format(
            "{ \"access_token\": \"%s\", \n\"expires_in\": \"%s\" }", accessToken, expireTime));
  }

  private Bundle getNotificationBundle(String notificationBundle) {
    File notificationFile = new File(classLoader.getResource(notificationBundle).getFile());
    String absolutePath = notificationFile.getAbsolutePath();
    return ap.readBundleFromFile(absolutePath);
  }
}
