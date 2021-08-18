package com.drajer.bsa.kar.model;

import com.drajer.bsa.ehr.service.EhrQueryService;
import com.drajer.bsa.kar.action.BsaActionStatus;
import com.drajer.bsa.model.BsaTypes;
import com.drajer.bsa.model.BsaTypes.BsaActionStatusType;
import com.drajer.bsa.model.KarExecutionState;
import com.drajer.bsa.model.KarProcessingData;
import com.drajer.bsa.scheduler.BsaScheduler;
import com.drajer.eca.model.TimingSchedule;
import com.drajer.ecrapp.util.ApplicationUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.DataRequirement;
import org.hl7.fhir.r4.model.PlanDefinition.ActionRelationshipType;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to represent the PlanDefinition Action. The model has been simplified for
 * processing as compared to the FHIR Resource for the Action to avoid all the nestings.
 *
 * @author nbashyam
 */
public abstract class BsaAction {

  private final Logger logger = LoggerFactory.getLogger(KnowledgeArtifact.class);

  /** The unique Id for the action. */
  protected String actionId;

  /** The type of action */
  protected BsaTypes.ActionType type;

  /** The list of named events upon which this action will be triggered, this may be empty. */
  protected Set<String> namedEventTriggers;

  /** The list of input data requirements required for processing of the action. */
  protected List<DataRequirement> inputData;

  /** The list of Resource Types summarized from input Data */
  protected HashMap<String, ResourceType> inputResourceTypes;

  /** The list of output data the action is supposed to create. */
  protected List<DataRequirement> outputData;

  /**
   * The conditions when present and evaluated to true, the action will be executed. If the
   * conditions are not evaluated to true, then the rest of the actions are not processed. If there
   * are more than one conditions in the List, then all of them have to be true.
   */
  private List<BsaCondition> conditions;

  /**
   * The list of related actions that have to be executed once this action is complete. The
   * ActionRelationshipType that is currently handled is only "before-start" per MedMorph. In the
   * future others may be handled.
   */
  private HashMap<ActionRelationshipType, Set<BsaRelatedAction>> relatedActions;

  /**
   * The timing data related to this action, which essentially provides a time offset for executing
   * this action once all the conditions are met.
   */
  private List<TimingSchedule> timingData;

  /**
   * The list of sub actions that need to be executed as part of the parent action, when the
   * conditions are met.
   */
  private List<BsaAction> subActions;

  /** The attribute that holds a definition of a measure in case of measure evaluation */
  private String measureUri;

  /** The scheduler that is required to be used to schedule jobs. */
  BsaScheduler scheduler;

  /** Attribute that can be set to ignore timers for testing through the same code paths */
  Boolean ignoreTimers;

  /** The method that all actions have to implement to process data. */
  public abstract BsaActionStatus process(KarProcessingData data, EhrQueryService ehrservice);

  public Boolean conditionsMet(KarProcessingData kd) {

    Boolean retVal = true;

    for (BsaCondition bc : conditions) {

      // If any of the conditions evaluate to be false, then the method returns false.
      if (!bc.getConditionProcessor().evaluateExpression(bc, this, kd)) {
        logger.info(" Condition Processing evaluated to false for action {}", this.getActionId());
        retVal = false;
      }
    }

    return retVal;
  }

  public void executeSubActions(KarProcessingData kd, EhrQueryService ehrService) {

    logger.info(" Start Executing Sub Actions for action {}", this.getActionId());

    for (BsaAction act : subActions) {

      logger.info(" Executing Action {}", act.getActionId());
      act.process(kd, ehrService);
    }

    logger.info(" Finished Executing Sub Actions for action {}", this.getActionId());
  }

  public void executeRelatedActions(KarProcessingData kd, EhrQueryService ehrService) {

    logger.info(" Start Executing Related Action for action {}", this.getActionId());

    for (Map.Entry<ActionRelationshipType, Set<BsaRelatedAction>> entry :
        relatedActions.entrySet()) {

      if (entry.getKey() == ActionRelationshipType.BEFORESTART) {

        Set<BsaRelatedAction> actions = entry.getValue();

        for (BsaRelatedAction ract : actions) {

          if (ract.getDuration() == null && ract.getAction() != null) {

            logger.info(
                " **** Start Executing Related Action : {} **** ", ract.getRelatedActionId());
            ract.getAction().process(kd, ehrService);
            logger.info(" **** Finished execuing the Related Action. **** ");

          } else if (ract.getDuration() != null && ract.getAction() != null) {

            logger.info(
                " Found the Related Action, with a duration so need to setup a timer to execute later ");

            // Save the execution state, before the scheduling of a job.
            KarExecutionState st =
                kd.getKarExecutionStateService().saveOrUpdate(kd.getKarExecutionState());

            Instant t = ApplicationUtils.convertDurationToInstant(ract.getDuration());

            if (t != null && !ignoreTimers)
              scheduler.scheduleJob(
                  st.getId(), ract.getAction().getActionId(), ract.getAction().getType(), t);
            else {
              logger.info(
                  " **** Start Executing Related Action : {} **** ", ract.getRelatedActionId());
              ract.getAction().process(kd, ehrService);
              logger.info(" **** Finished execuing the Related Action. **** ");
            }

          } else {
            logger.info(
                " Related Action not found, so skipping executing of action {} ",
                ract.getRelatedActionId());
          }
        }

      } else {

        logger.info(
            " Not executing Related Action because relationship type is {}", entry.getKey());
      }
    }

    logger.info(" Finished Executing Related Action for action {}", this.getActionId());
  }

  public BsaActionStatusType processTimingData(KarProcessingData kd) {

    if (timingData != null && timingData.size() > 0 && !ignoreTimers) {

      // Check and setup future timers.

      return BsaActionStatusType.Scheduled;
    } else {

      logger.info(" No timing data, so continue with the execution of the action ");
      return BsaActionStatusType.InProgress;
    }
  }

  public BsaAction() {

    actionId = "";
    namedEventTriggers = new HashSet<>();
    inputData = new ArrayList<DataRequirement>();
    inputResourceTypes = new HashMap<>();
    outputData = new ArrayList<DataRequirement>();
    conditions = new ArrayList<BsaCondition>();
    relatedActions = new HashMap<>();
    timingData = new ArrayList<TimingSchedule>();
    subActions = new ArrayList<>();
    measureUri = "";
  }

  public String getActionId() {
    return actionId;
  }

  public void setActionId(String actionId, String planDefinitionContext) {
    this.actionId = String.format("%s-PlanDefinition/%s", actionId, planDefinitionContext);
  }

  public Set<String> getNamedEventTriggers() {
    return namedEventTriggers;
  }

  public void setNamedEventTriggers(Set<String> namedEventTriggers) {
    this.namedEventTriggers = namedEventTriggers;
  }

  public List<DataRequirement> getInputData() {
    return inputData;
  }

  public void setInputData(List<DataRequirement> inputData) {
    this.inputData = inputData;
  }

  public List<DataRequirement> getOutputData() {
    return outputData;
  }

  public void setOutputData(List<DataRequirement> outputData) {
    this.outputData = outputData;
  }

  public List<BsaCondition> getConditions() {
    return conditions;
  }

  public void setConditions(List<BsaCondition> conditions) {
    this.conditions = conditions;
  }

  public HashMap<ActionRelationshipType, Set<BsaRelatedAction>> getRelatedActions() {
    return relatedActions;
  }

  public void setRelatedActions(
      HashMap<ActionRelationshipType, Set<BsaRelatedAction>> relatedActions) {
    this.relatedActions = relatedActions;
  }

  public List<TimingSchedule> getTimingData() {
    return timingData;
  }

  public void setTimingData(List<TimingSchedule> timingData) {
    this.timingData = timingData;
  }

  public HashMap<String, ResourceType> getInputResourceTypes() {
    return inputResourceTypes;
  }

  public void setInputResourceTypes(HashMap<String, ResourceType> inputResourceTypes) {
    this.inputResourceTypes = inputResourceTypes;
  }

  public List<BsaAction> getSubActions() {
    return subActions;
  }

  public void setSubActions(List<BsaAction> subActions) {
    this.subActions = subActions;
  }

  public void addInputResourceType(String id, ResourceType rt) {

    if (!inputResourceTypes.containsKey(id)) {
      inputResourceTypes.put(id, rt);
    } else {

      //  nothing to do , it is already present.
    }
  }

  public void addAction(BsaAction action) {
    subActions.add(action);
  }

  public void addCondition(BsaCondition cond) {
    conditions.add(cond);
  }

  public BsaTypes.ActionType getType() {
    return type;
  }

  public void setType(BsaTypes.ActionType type) {
    this.type = type;
  }

  public String getMeasureUri() {
    return measureUri;
  }

  public void setMeasureUri(String measureUri) {
    this.measureUri = measureUri;
  }

  public BsaScheduler getScheduler() {
    return scheduler;
  }

  public void setScheduler(BsaScheduler scheduler) {
    this.scheduler = scheduler;
  }

  public Boolean getIgnoreTimers() {
    return ignoreTimers;
  }

  public void setIgnoreTimers(Boolean ignoreTimers) {
    this.ignoreTimers = ignoreTimers;
  }

  public void addRelatedAction(BsaRelatedAction ract) {

    if (relatedActions.containsKey(ract.getRelationship())) {
      relatedActions.get(ract.getRelationship()).add(ract);
    } else {
      Set<BsaRelatedAction> racts = new HashSet<BsaRelatedAction>();
      racts.add(ract);
      relatedActions.put(ract.getRelationship(), racts);
    }
  }

  public void printSummary() {

    logger.info(" **** START Printing Action **** ({})", actionId);

    logger.info(" Action Type : {}", type.toString());

    namedEventTriggers.forEach(ne -> logger.info(" Named Event : ({})", ne));

    conditions.forEach(con -> con.log());

    if (relatedActions != null && relatedActions.size() > 0) {

      logger.info(" ****** Number of Related Actions : ({}) ****** ", relatedActions.size());

      for (Map.Entry<ActionRelationshipType, Set<BsaRelatedAction>> entry :
          relatedActions.entrySet()) {

        logger.info(" ****** RelationshipType : ({}) ****** ", entry.getKey().toString());
        Set<BsaRelatedAction> racts = entry.getValue();

        for (BsaRelatedAction ract : racts) {

          logger.info(" ******** Related Action Id : ({}) ******** ", ract.getRelatedActionId());
        }
      }
    }

    if (subActions.size() > 0) {

      logger.info(" ****** Number of SubActions : ({}) ****** ", subActions.size());
      for (BsaAction subAct : subActions) {

        logger.info(" ******** Sub Action Id : ({}) ******** ", subAct.getActionId());

        if (subAct.getRelatedActions() != null && subAct.getRelatedActions().size() > 0) {

          for (Map.Entry<ActionRelationshipType, Set<BsaRelatedAction>> entry :
              subAct.getRelatedActions().entrySet()) {

            logger.info(
                " ********** RelationshipType : ({}) ********** ", entry.getKey().toString());
            Set<BsaRelatedAction> racts = entry.getValue();

            for (BsaRelatedAction ract : racts) {

              logger.info(
                  " ************ Related Action Id : ({}) ************ ",
                  ract.getRelatedActionId());
            }
          }
        } else {

          logger.info(
              " ********** No Related Actions for sub Action : ({}) ********** ",
              subAct.getActionId());
        }
      }

    } else {
      logger.info(" ******** No Sub Actions for : ({}) ******** ", actionId);
    }

    logger.info(" **** END Printing Action **** {}", actionId);
  }

  public void log() {

    logger.info(" **** START Printing Action **** {}", actionId);

    logger.info(" Action Type : {}", type.toString());
    namedEventTriggers.forEach(ne -> logger.info(" Named Event : {}", ne));

    for (DataRequirement inp : inputData) {

      logger.info(" Input Data Req Id : {}", inp.getId());
      logger.info(" Input Data Type : {}", inp.getType());

      if (inp.getProfile() != null && inp.getProfile().size() >= 1) {
        logger.info(" Input Data Profile : {}", inp.getProfile().get(0).asStringValue());
      }

      if (inp.hasCodeFilter()) {

        if (inp.getCodeFilterFirstRep().hasPath()) {
          logger.info(" Code Filter Path : {}", inp.getCodeFilterFirstRep().getPath());
        }

        if (inp.getCodeFilterFirstRep().hasValueSet()) {
          logger.info(" Code Filter Value Set : {}", inp.getCodeFilterFirstRep().getValueSet());
        }
      }

      if (inp.hasCodeFilter()
          && inp.getCodeFilterFirstRep().hasCode()
          && inp.getCodeFilterFirstRep().getCodeFirstRep() != null) {
        logger.info(
            " Input Code Filter Resource : {} ",
            inp.getCodeFilterFirstRep().getCodeFirstRep().getCode());
      }
    }

    for (DataRequirement output : outputData) {

      logger.info(" Output Data Req Id : {}", output.getId());
      logger.info(" Output Data Type : {}", output.getType());

      if (output.getProfile() != null && output.getProfile().size() >= 1) {
        logger.info(" Output Data Profile : {}", output.getProfile().get(0).asStringValue());
      }
    }

    conditions.forEach(con -> con.log());

    if (relatedActions != null)
      relatedActions.forEach((key, value) -> value.forEach(act -> act.log()));

    timingData.forEach(td -> td.print());

    logger.info(" Start Printing Sub Actions ");
    subActions.forEach(act -> act.log());
    logger.info(" Finished Printing Sub Actions ");

    logger.info(" **** END Printing Action **** {}", actionId);
  }
}
