package com.drajer.bsa.utils;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.drajer.eca.model.MatchedTriggerCodes;
import com.google.common.base.Strings;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetComposeComponent;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <h1>BsaServiceUtils</h1>
 *
 * This class implements a few utilities to be used by many of the different services.
 *
 * @author nbashyam
 */
@Service
public class BsaServiceUtils {

  private static final Logger logger = LoggerFactory.getLogger(BsaServiceUtils.class);

  @Autowired
  @Qualifier("jsonParser")
  IParser jsonParser;

  @Value("${bsa.output.directory}")
  String debugDirectory;

  @Value("${eCRFhir.endpoint}")
  String eCRonFhirEndpoint;

  private static final String FHIR_PATH_VARIABLE_PREFIX = "%";

  public static String getFhirPathVariableString(String id) {

    if (id.length() > 2) {

      String part1 = id.substring(0, 1).toLowerCase();
      String part2 = id.substring(1);

      String result = FHIR_PATH_VARIABLE_PREFIX + part1 + part2;

      return result;
    }

    return id.toLowerCase();
  }

  public Bundle readKarFromFile(String filePath) {

    logger.info("About to read KAR File {}", filePath);
    Bundle bundle = null;
    try (InputStream in = new FileInputStream(new File(filePath))) {
      logger.info("Start Reading KAR File ");

      bundle = jsonParser.parseResource(Bundle.class, in);
      logger.info("Completed Reading KAR File");
    } catch (Exception e) {
      logger.error("Exception Reading KAR File", e);
    }
    return bundle;
  }

  public static Pair<Boolean, MatchedTriggerCodes> isCodeableConceptPresentInValueSet(
      ValueSet vs, CodeableConcept cd, Boolean valElem) {

    Pair<Boolean, MatchedTriggerCodes> retVal = null;
    Boolean matchFound = false;
    MatchedTriggerCodes mtc = null;

    if (cd != null && cd.getCoding().size() > 0) {

      for (Coding c : cd.getCoding()) {

        Pair<Boolean, Pair<String, String>> retInfo = isCodingPresentInValueSet(vs, c);

        if (retInfo != null) {

          logger.info(" Match Found for code {} | {}", retInfo.getValue0(), retInfo.getValue1());

          if (mtc != null) {
            mtc = new MatchedTriggerCodes();
            mtc.setValueSet(vs.getUrl());
            mtc.setValueSetVersion(vs.getVersion());
            matchFound = true;

            if (valElem) {
              logger.info(" Matched Code is part of a Value Element ");
              mtc.addValue(retInfo.getValue0() + "|" + retInfo.getValue1());
            } else {
              logger.info(" Matched Code is part of a Code Element ");
              mtc.addCode(retInfo.getValue0() + "|" + retInfo.getValue1());
            }
          }
        }
      }
    }

    if (matchFound) {
      retVal = new Pair<Boolean, MatchedTriggerCodes>(true, mtc);
    }

    return retVal;
  }

  public static Pair<Boolean, Pair<String, String>> isCodingPresentInValueSet(
      ValueSet vs, Coding coding) {

    Pair<Boolean, Pair<String, String>> retVal = null;

    if (coding != null && isCodePresentInValueSet(vs, coding.getSystem(), coding.getCode())) {
      Pair<String, String> matchedCodeInfo = new Pair<>(coding.getSystem(), coding.getCode());
      retVal = new Pair<Boolean, Pair<String, String>>(true, matchedCodeInfo);
    }

    return retVal;
  }

  public static Boolean isCodePresentInValueSet(ValueSet vs, String system, String code) {

    Boolean retVal = false;

    if (vs.hasCompose()) {

      ValueSetComposeComponent vsc = vs.getCompose();

      List<ConceptSetComponent> cscs = vsc.getInclude();

      if (cscs != null) {

        for (ConceptSetComponent csc : cscs) {

          if (csc.getSystem() != null && csc.getSystem().contentEquals(system)) {

            logger.info(" Found Code System {} in value set ", system);

            List<ConceptReferenceComponent> crcs = csc.getConcept();

            if (crcs != null) {

              for (ConceptReferenceComponent crc : crcs) {

                if (crc.getCode().contentEquals(code)) {
                  logger.info(" Found code system {} and code {} in value set ", system, code);
                  retVal = true;
                }
              }
            }
          }
        }
      }
    }

    return retVal;
  }

  public void saveResourceToFile(Resource res) {

    String fileName =
        debugDirectory + res.getResourceType().toString() + "_" + res.getId() + ".json";

    String data = jsonParser.encodeResourceToString(res);

    try (DataOutputStream outStream =
        new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)))) {

      logger.info(" Writing data to file: {}", fileName);
      outStream.writeBytes(data);
    } catch (IOException e) {
      logger.debug(" Unable to write data to file: {}", fileName, e);
    }
  }

  public void saveResourceToClient(Resource res) {
    FhirContext context = FhirContext.forCached(res.getStructureFhirVersionEnum());
    if (!Strings.isNullOrEmpty(eCRonFhirEndpoint)) {
      IGenericClient client = context.newRestfulGenericClient(eCRonFhirEndpoint);
      context.getRestfulClientFactory().setSocketTimeout(30 * 1000);
      logger.info(" Executing the submission of the Report");
      if (res instanceof Bundle) {
        Bundle bundle = (Bundle) res;
        Bundle responseBundle =
            (Bundle)
                client
                    .operation()
                    .onServer()
                    .named("$process-message-bundle")
                    .withParameter(Parameters.class, "content", bundle)
                    .returnResourceType(Bundle.class)
                    .encodedJson()
                    .execute();

        logger.info(
            "Response Bundle:::::{}",
            context.newJsonParser().encodeResourceToString(responseBundle));
      } else {
        logger.debug(
            "Unexpected resource for save to client : {}",
            context.newJsonParser().encodeResourceToString(res));
      }
    }
  }
}
