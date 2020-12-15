package com.drajer.ecr.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.drajer.eca.model.PatientExecutionState;
import com.drajer.ecr.it.common.BaseIntegrationTest;
import com.drajer.ecr.it.common.WireMockHelper;
import com.drajer.ecrapp.model.Eicr;
import com.drajer.sof.model.LaunchDetails;
import com.drajer.test.util.TestDataGenerator;
import com.drajer.test.util.TestUtils;
import com.drajer.test.util.ValidationUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@RunWith(Parameterized.class)
public class ITValidateEicrDoc extends BaseIntegrationTest {

  private String testCaseId;
  String clientDetailsFile;
  String systemLaunchFile;
  Map<String, List<String>> allResourceFiles;
  Map<String, ?> allResourceMapping;
  Map<String, ?> allOtherMapping;
  List<Map<String, String>> fieldsToValidate;

  public ITValidateEicrDoc(
      String testCaseId,
      String clientDetails,
      String payLoad,
      Map<String, List<String>> resourceFiles,
      List<Map<String, String>> validateFields,
      Map<String, ?> resourceMapping,
      Map<String, ?> otherMapping) {

    this.testCaseId = testCaseId;
    this.clientDetailsFile = clientDetails;
    this.systemLaunchFile = payLoad;
    this.allResourceFiles = resourceFiles;
    this.fieldsToValidate = validateFields;
    this.allResourceMapping = resourceMapping;
    this.allOtherMapping = otherMapping;
  }

  private static final Logger logger = LoggerFactory.getLogger(ITValidateEicrDoc.class);

  private final String systemLaunchURI = "/api/systemLaunch";

  private static String systemLaunchInputData;
  private String patientId;
  private String encounterID;
  private String fhirServerUrl;

  private LaunchDetails launchDetails;
  private PatientExecutionState state;

  WireMockHelper stubHelper;

  @Before
  public void launchTestSetUp() throws IOException {
    logger.info("Executing Tests with TestCase: " + testCaseId);
    tx = session.beginTransaction();

    // Data Setup
    createClientDetails(clientDetailsFile);
    systemLaunchInputData = getSystemLaunchInputData(systemLaunchFile);
    JSONObject jsonObject = new JSONObject(systemLaunchInputData);
    patientId = (String) jsonObject.get("patientId");
    if (jsonObject.get("encounterId") instanceof String) {
      encounterID = (String) jsonObject.get("encounterId");
    }
    fhirServerUrl = (String) jsonObject.get("fhirServerURL");

    session.flush();
    tx.commit();

    stubHelper = new WireMockHelper(baseUrl, wireMockHttpPort);
    logger.info("Creating wiremockstubs..");
    stubHelper.stubResources(allResourceMapping);
    stubHelper.stubAuthAndMetadata(allOtherMapping);
  }

  @After
  public void cleanUp() {
    if (stubHelper != null) {
      stubHelper.stopMockServer();
    }
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {

    List<TestDataGenerator> testDataGenerator = new ArrayList<>();
    testDataGenerator.add(new TestDataGenerator("test-yaml/headerSection.yaml"));
    testDataGenerator.add(new TestDataGenerator("test-yaml/problemSection.yaml"));
    testDataGenerator.add(new TestDataGenerator("test-yaml/encounterSection.yaml"));
    testDataGenerator.add(new TestDataGenerator("test-yaml/resultSection.yaml"));
    testDataGenerator.add(new TestDataGenerator("test-yaml/immunizationSection.yaml"));
    testDataGenerator.add(new TestDataGenerator("test-yaml/socialHistorySection.yaml"));

    int totalTestcount =
        testDataGenerator.get(0).getAllTestCases().size()
            + testDataGenerator.get(1).getAllTestCases().size()
            + testDataGenerator.get(2).getAllTestCases().size()
            + testDataGenerator.get(3).getAllTestCases().size()
            + testDataGenerator.get(4).getAllTestCases().size()
            + testDataGenerator.get(5).getAllTestCases().size();

    Object[][] data = new Object[totalTestcount][7];

    int count = 0;
    for (TestDataGenerator testData : testDataGenerator) {

      Set<String> testCaseSet = testData.getAllTestCases();

      for (String testCase : testCaseSet) {

        data[count][0] = testCase;
        data[count][1] = testData.getTestFile(testCase, "ClientDataToBeSaved");
        data[count][2] = testData.getTestFile(testCase, "SystemLaunchPayload");
        data[count][3] = testData.getResourceFiles(testCase);
        data[count][4] = testData.getValidate(testCase);
        data[count][5] = testData.getResourceMappings(testCase);
        data[count][6] = testData.getOtherMappings(testCase);

        count++;
      }
    }

    return Arrays.asList(data);
  }

  @Test
  public void testEicrDocument() throws Exception {

    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<String> entity = new HttpEntity<String>(systemLaunchInputData, headers);
    logger.info("Invoking systemLaunch...");
    logger.info("Payload: \n" + systemLaunchInputData);
    ResponseEntity<String> response =
        restTemplate.exchange(
            createURLWithPort(systemLaunchURI), HttpMethod.POST, entity, String.class);
    logger.info("Received Response. Waiting for EICR generation.....");

    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    assertTrue(response.getBody().contains("App is launched successfully"));

    Eicr createEicr = getCreateEicrDocument();
    assertNotNull(createEicr.getEicrData());

    getLaunchDetailAndStatus();
    ValidationUtils.setLaunchDetails(launchDetails);

    Document eicrXmlDoc = TestUtils.getXmlDocuments(createEicr.getEicrData());
    // validateXml(eicrXmlDoc);
  }

  private void getLaunchDetailAndStatus() {

    try {
      Criteria criteria = session.createCriteria(LaunchDetails.class);
      criteria.add(Restrictions.eq("ehrServerURL", fhirServerUrl));
      criteria.add(Restrictions.eq("launchPatientId", patientId));
      criteria.add(Restrictions.eq("encounterId", encounterID));
      launchDetails = (LaunchDetails) criteria.uniqueResult();

      state = mapper.readValue(launchDetails.getStatus(), PatientExecutionState.class);
      session.refresh(launchDetails);

    } catch (Exception e) {

      fail(e.getMessage() + "Exception occured retreving launchdetail and status");
    }
  }

  private Eicr getCreateEicrDocument() {

    try {

      do {

        // Minimum 2 sec is required as App will execute
        // createEicr workflow after 2 sec as per eRSD.
        Thread.sleep(2000);
        getLaunchDetailAndStatus();

      } while (!state.getCreateEicrStatus().getEicrCreated());

      return (session.get(Eicr.class, Integer.parseInt(state.getCreateEicrStatus().geteICRId())));

    } catch (Exception e) {
      fail(e.getMessage() + "Error while retrieving EICR document");
    }
    return null;
  }

  private void validateXml(Document eicrXml) throws XPathExpressionException {
    final XPath xPath = XPathFactory.newInstance().newXPath();

    if (fieldsToValidate != null) {

      for (Map<String, String> field : fieldsToValidate) {

        try {

          String xPathExp = field.get("xPath");
          if (field.containsKey("count")) {
            try {
              NodeList nodeList =
                  (NodeList) xPath.compile(xPathExp).evaluate(eicrXml, XPathConstants.NODESET);
              assertEquals(xPathExp, Integer.parseInt(field.get("count")), nodeList.getLength());
            } catch (XPathExpressionException e) {
              fail(e.getMessage() + ": Failed evaluate field " + xPathExp);
            }
          } else {

            for (Entry<String, String> set : field.entrySet()) {

              if (!set.getKey().equalsIgnoreCase("xPath")) {
                String xPathFullExp = xPathExp + "/" + set.getKey();
                try {
                  String fieldValue =
                      (String) xPath.compile(xPathFullExp).evaluate(eicrXml, XPathConstants.STRING);
                  assertEquals(xPathFullExp, set.getValue(), fieldValue);
                } catch (XPathExpressionException e) {
                  fail(e.getMessage() + ": Failed evaluate field " + xPathExp);
                }
              }
            }
          }

        } catch (Exception e) {
          fail(e.getMessage() + ": This exception is not expected fix the test");
        }
      }

    } else {

      fail("validate field is not configured in the test");
    }
  }
}