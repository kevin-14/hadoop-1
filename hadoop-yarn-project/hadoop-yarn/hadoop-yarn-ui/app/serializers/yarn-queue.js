import DS from 'ember-data';

export default DS.JSONAPISerializer.extend({

    normalizeSingleResponse(store, primaryModelClass, payload, id,
      requestType) {
      var children = [];
      if (payload.queues) {
        payload.queues.queue.forEach(function(queue) {
          children.push(queue.queueName);
        });
      }

      var fixedPayload = {
        id: id,
        type: primaryModelClass.modelName, // yarn-queue
        attributes: {
          name: payload.queueName,
          parent: payload.myParent,
          children: children,
          capacity: payload.capacity,
          usedCapacity: payload.usedCapacity,
          maxCapacity: payload.maxCapacity,
          absCapacity: payload.absoluteCapacity,
          absMaxCapacity: payload.absoluteMaxCapacity,
          absUsedCapacity: payload.absoluteUsedCapacity,
          state: payload.state,
          userLimit: payload.userLimit,
          userLimitFactor: payload.userLimitFactor,
          preemptionDisabled: payload.preemptionDisabled
        },
      };

      return this._super(store, primaryModelClass, fixedPayload, id,
        requestType);
    },

    handleQueue(store, primaryModelClass, payload, id, requestType) {
      var data = [];

      data.push(this.normalizeSingleResponse(store, primaryModelClass,
        payload,
        id, requestType));

      if (payload.queues) {
        for (var i = 0; i < payload.queues.queue.length; i++) {
          var queue = payload.queues.queue[i];
          queue.myParent = payload.queueName;
          data = data.concat(this.handleQueue(store, primaryModelClass, queue,
            queue.queueName,
            requestType));
        }
      }

      return data;
    },

    normalizeArrayResponse(store, primaryModelClass, payload, id,
      requestType) {
      var normalizedArrayResponse = {};
      normalizedArrayResponse.data = this.handleQueue(store,
        primaryModelClass,
        payload.scheduler.schedulerInfo, "root", requestType);

      return normalizedArrayResponse;

      /*
      // return expected is { data: [ {}, {} ] }
      var normalizedArrayResponse = {};

      // payload has apps : { app: [ {},{},{} ]  }
      // need some error handling for ex apps or app may not be defined.
      normalizedArrayResponse.data = payload.apps.app.map(singleApp => { 
        return this.normalizeSingleResponse(store, primaryModelClass, singleApp, singleApp.id, requestType);
      }, this);
      return normalizedArrayResponse;
      */
    }
});