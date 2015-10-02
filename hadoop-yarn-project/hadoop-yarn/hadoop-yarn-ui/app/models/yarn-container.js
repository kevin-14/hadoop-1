import DS from 'ember-data';

export default DS.Model.extend({
  containerId: DS.attr('string'),
  allocatedMB: DS.attr('number'),
  allocatedVCores: DS.attr('number'),
  assignedNodeId: DS.attr('string'),
  priority: DS.attr('number'),
  startedTime: DS.attr('number'),
  finishedTime: DS.attr('number'),
  logUrl: DS.attr('string'),
  containerExitStatus: DS.attr('number'),
  containerState: DS.attr('string'),
  nodeHttpAddress: DS.attr('string')
});