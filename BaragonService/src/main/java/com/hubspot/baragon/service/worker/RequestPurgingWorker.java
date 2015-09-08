package com.hubspot.baragon.service.worker;

import java.util.Date;
import java.util.List;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.hubspot.baragon.data.BaragonAgentResponseDatastore;
import com.hubspot.baragon.data.BaragonRequestDatastore;
import com.hubspot.baragon.data.BaragonResponseHistoryDatastore;
import com.hubspot.baragon.data.BaragonStateDatastore;
import com.hubspot.baragon.models.BaragonRequest;
import com.hubspot.baragon.models.BaragonResponse;
import com.hubspot.baragon.models.InternalRequestStates;
import com.hubspot.baragon.models.InternalStatesMap;
import com.hubspot.baragon.service.config.BaragonConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestPurgingWorker implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(RequestPurgingWorker.class);

  private static final long DAY_IN_MS = 1000 * 60 * 60 * 24;

  private final BaragonRequestDatastore requestDatastore;
  private final BaragonConfiguration configuration;
  private final BaragonAgentResponseDatastore agentResponseDatastore;
  private final BaragonResponseHistoryDatastore responseHistoryDatastore;
  private final BaragonStateDatastore stateDatastore;

  @Inject
  public RequestPurgingWorker(BaragonRequestDatastore requestDatastore,
                              BaragonConfiguration configuration,
                              BaragonAgentResponseDatastore agentResponseDatastore,
                              BaragonResponseHistoryDatastore responseHistoryDatastore,
                              BaragonStateDatastore stateDatastore) {
    this.requestDatastore = requestDatastore;
    this.configuration = configuration;
    this.agentResponseDatastore = agentResponseDatastore;
    this.responseHistoryDatastore = responseHistoryDatastore;
    this.stateDatastore = stateDatastore;
  }

  private enum PurgeAction {
    PURGE, SAVE, NONE
  }

  @Override
  public void run() {
   long referenceTime = System.currentTimeMillis() - (configuration.getHistoryConfiguration().getPurgeOldRequestsAfterDays() * DAY_IN_MS);
    cleanUpActiveRequests(referenceTime);
    if (configuration.getHistoryConfiguration().isPurgeOldRequests()) {
      purgeHistoricalRequests(referenceTime);
    }
  }

  public void cleanUpActiveRequests(long referenceTime) {
    List<String> allMaybeActiveRequestIds = requestDatastore.getAllRequestIds();
    for (String requestId : allMaybeActiveRequestIds) {
      Optional<InternalRequestStates> maybeState = requestDatastore.getRequestState(requestId);
      switch(getPurgeActionForMaybeActiveRequest(requestId, referenceTime, maybeState)) {
        case PURGE:
          requestDatastore.deleteRequest(requestId);
          break;
        case SAVE:
          Optional<BaragonRequest> maybeRequest = requestDatastore.getRequest(requestId);
          if (maybeRequest.isPresent()) {
            BaragonResponse response = new BaragonResponse(maybeRequest.get().getLoadBalancerRequestId(), InternalStatesMap.getRequestState(maybeState.get()), requestDatastore.getRequestMessage(maybeRequest.get().getLoadBalancerRequestId()), Optional.of(agentResponseDatastore.getLastResponses(maybeRequest.get().getLoadBalancerRequestId())), maybeRequest);
            responseHistoryDatastore.addResponse(maybeRequest.get().getLoadBalancerService().getServiceId(), maybeRequest.get().getLoadBalancerRequestId(), response);
            requestDatastore.deleteRequest(requestId);
          } else {
            LOG.warn(String.format("Could not get request data to save history for request %s", requestId));
          }
          break;
        case NONE:
        default:
          break;
      }
    }
  }

  private PurgeAction getPurgeActionForMaybeActiveRequest(String requestId, long referenceTime, Optional<InternalRequestStates> maybeState) {
    Optional<Long> maybeUpdatedAt = requestDatastore.getRequestUpdatedAt(requestId);
    if (!maybeState.isPresent() || InternalStatesMap.isRemovable(maybeState.get())) {
      if (configuration.getHistoryConfiguration().isPurgeOldRequests()) {
        if (shouldPurge(maybeUpdatedAt, referenceTime)) {
          LOG.trace(String.format("Updated at time: %s is earlier than reference time: %s, purging request %s", maybeUpdatedAt.get(), referenceTime, requestId));
          return PurgeAction.PURGE;
        } else {
          return PurgeAction.SAVE;
        }
      } else {
        return PurgeAction.SAVE;
      }
    } else {
      return PurgeAction.NONE;
    }
  }

  private void purgeHistoricalRequests(long referenceTime) {
    for (String serviceId : responseHistoryDatastore.getServiceIds()) {
      if (!serviceId.equals("requestIdMapping")) {
        List<String> requestIds = responseHistoryDatastore.getRequestIdsForService(serviceId);
        if (stateDatastore.serviceExists(serviceId)) {
          if (!requestIds.isEmpty()) {
            for (String requestId : requestIds) {
              Optional<Long> maybeUpdatedAt = responseHistoryDatastore.getRequestUpdatedAt(serviceId, requestId);
              if (shouldPurge(maybeUpdatedAt, referenceTime)) {
                LOG.trace(String.format("Updated at time: %s is earlier than reference time: %s, purging request %s", maybeUpdatedAt.get(), referenceTime, requestId));
                responseHistoryDatastore.deleteResponse(serviceId, requestId);
              }
            }
          }
        } else {
          responseHistoryDatastore.deleteResponses(serviceId);
        }
      }
    }
  }

  private boolean shouldPurge(Optional<Long> maybeUpdatedAt, long referenceTime) {
    return (maybeUpdatedAt.isPresent() && maybeUpdatedAt.get() < referenceTime) || (!maybeUpdatedAt.isPresent() && configuration.getHistoryConfiguration().isPurgeWhenDateNotFound());
  }
}