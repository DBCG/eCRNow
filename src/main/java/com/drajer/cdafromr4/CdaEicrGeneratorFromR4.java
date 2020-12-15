package com.drajer.cdafromr4;

import com.drajer.cda.utils.CdaGeneratorUtils;
import com.drajer.sof.model.LaunchDetails;
import com.drajer.sof.model.R4FhirData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CdaEicrGeneratorFromR4 {

  private static final Logger logger = LoggerFactory.getLogger(CdaEicrGeneratorFromR4.class);

  private CdaEicrGeneratorFromR4() {}

  public static String convertR4FhirBundletoCdaEicr(R4FhirData data, LaunchDetails details) {

    StringBuilder eICR = new StringBuilder();

    if (data != null) {

      logger.info(" Preparing Jurisdiction data");
      data.prepareJurisdicationData();

      if (data.hasRequiredDataForEicr()) {
        logger.info(" Bundle has required data for Eicr ");
        eICR.append(CdaHeaderGenerator.createCdaHeader(data, details));
        eICR.append(CdaBodyGenerator.generateCdaBody(data, details));
        eICR.append(CdaGeneratorUtils.getEndXMLHeaderForCdaDocument());
      } else {
        logger.error(" Fhir Data not ready to be used for creating a CDA Document ");
      }

    } else {

      logger.error(" No Fhir Bundle Available to create CDA Documents ");
    }

    return eICR.toString();
  }
}