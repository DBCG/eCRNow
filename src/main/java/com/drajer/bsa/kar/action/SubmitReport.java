package com.drajer.bsa.kar.action;

import ca.uhn.fhir.context.FhirContext;
import com.drajer.bsa.ehr.service.EhrQueryService;
import com.drajer.bsa.kar.model.BsaAction;
import com.drajer.bsa.model.KarProcessingData;
import com.drajer.bsa.utils.BsaServiceUtils;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SubmitReport extends BsaAction {

  private final Logger logger = LoggerFactory.getLogger(SubmitReport.class);

  @Value("${eCRFhir.endpoint}")
  private String eCRonFhirEndpoint;

  @Autowired BsaServiceUtils serviceUtils;

  private static final FhirContext context = FhirContext.forR4();

  @Override
  public BsaActionStatus process(KarProcessingData data, EhrQueryService ehrService) {

    logger.info(" Executing the submission of the Report");

    HashMap<String, HashMap<String, Resource>> res = data.getActionOutputData();

    for (Map.Entry<String, HashMap<String, Resource>> entry : res.entrySet()) {

      logger.info("Submitting data to file for {}", entry.getKey());

      HashMap<String, Resource> resOutput = entry.getValue();

      for (Map.Entry<String, Resource> resEnt : resOutput.entrySet()) {

        logger.info(" Submitting Data to file for {}", resEnt.getKey());
        serviceUtils.saveResourceToClient(resEnt.getValue());
      }
    }

    return null;
  }

  public void setBsaUtils(BsaServiceUtils utils) {
    this.serviceUtils = utils;
  }
}
