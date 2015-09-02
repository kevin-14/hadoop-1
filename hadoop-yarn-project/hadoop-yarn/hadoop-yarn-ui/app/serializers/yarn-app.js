import DS from 'ember-data';

export default DS.JSONAPISerializer.extend({
  normalizeStartTime(timestamp) {
      var a = new Date(timestamp);
      var months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug',
        'Sep', 'Oct', 'Nov', 'Dec'
      ];
      var year = a.getFullYear();
      var month = months[a.getMonth()];
      var date = a.getDate();
      var hour = a.getHours();
      var min = a.getMinutes();
      var sec = a.getSeconds();
      var time = date + ' ' + month + ' ' + year + ' ' + hour + ':' + min +
        ':' + sec;
      return time;
    },

    normalizeElapsedTime(timeInMs) {
      var sec_num = timeInMs / 1000; // don't forget the second param
      var hours = Math.floor(sec_num / 3600);
      var minutes = Math.floor((sec_num - (hours * 3600)) / 60);
      var seconds = sec_num - (hours * 3600) - (minutes * 60);

      var timeStr = "";

      if (hours > 0) {
        timeStr = hours + ' Hrs ';
      }
      if (minutes > 0 || hours > 0) {
        timeStr += minutes + ' Mins ';
      }
      if (seconds > 0) {
        timeStr += Math.round(seconds) + " Secs";
      }
      return timeStr;
    },

    normalizeSingleResponse(store, primaryModelClass, payload, id,
      requestType) {
      var fixedPayload = {
        id: id,
        type: primaryModelClass.modelName, // yarn-app
        attributes: {
          appName: payload.name,
          user: payload.user,
          queue: payload.queue,
          state: payload.state,
          startTime: this.normalizeStartTime(payload.startedTime),
          elapsedTime: this.normalizeElapsedTime(payload.elapsedTime),
        }
      };

      return this._super(store, primaryModelClass, fixedPayload, id,
        requestType);
    },

    normalizeArrayResponse(store, primaryModelClass, payload, id,
      requestType) {
      // return expected is { data: [ {}, {} ] }
      var normalizedArrayResponse = {};

      // payload has apps : { app: [ {},{},{} ]  }
      // need some error handling for ex apps or app may not be defined.
      normalizedArrayResponse.data = payload.apps.app.map(singleApp => {
        return this.normalizeSingleResponse(store, primaryModelClass,
          singleApp, singleApp.id, requestType);
      }, this);
      return normalizedArrayResponse;
    }
});