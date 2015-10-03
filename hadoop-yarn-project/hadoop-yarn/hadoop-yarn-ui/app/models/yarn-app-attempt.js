import DS from 'ember-data';
import Converter from 'yarn-ui/utils/converter';

export default DS.Model.extend({
  startTime: DS.attr('string'),
  finishedTime: DS.attr('string'),
  containerId: DS.attr('string'),
  nodeHttpAddress: DS.attr('string'),
  nodeId: DS.attr('string'),
  logsLink: DS.attr('string'),

  startTs: function() {
    return Converter.dateToTimeStamp(this.get("startTime"));
  }.property("startTime"),

  finishedTs: function() {
    var ts = Converter.dateToTimeStamp(this.get("finishedTime"));
    return ts;
  }.property("finishedTime"),

  shortAppAttemptId: function() {
    return "attempt_" + 
           parseInt(Converter.containerIdToAttemptId(this.get("containerId")).split("_")[3]);
  }.property("containerId"),

  appAttemptId: function() {
    return Converter.containerIdToAttemptId(this.get("containerId"));
  }.property("containerId")
});