import DS from 'ember-data';

export default DS.Model.extend({
  startTime: DS.attr('string'),
  containerId: DS.attr('string'),
  nodeHttpAddress: DS.attr('string'),
  nodeId: DS.attr('string'),
  logsLink: DS.attr('string')
});