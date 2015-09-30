import DS from 'ember-data';

export default DS.Model.extend({
  appsSubmitted: DS.attr('number'),
  appsCompleted: DS.attr('number'),
  appsPending: DS.attr('number'),
  appsRunning: DS.attr('number'),
  appsFailed: DS.attr('number'),
  appsKilled: DS.attr('number'),
  reservedMB: DS.attr('number'),
  availableMB: DS.attr('number'),
  allocatedMB: DS.attr('number'),
  reservedVirtualCores: DS.attr('number'),
  availableVirtualCores: DS.attr('number'),
  allocatedVirtualCores: DS.attr('number'),
  containersAllocated: DS.attr('number'),
  containersReserved: DS.attr('number'),
  containersPending: DS.attr('number'),
  totalMB: DS.attr('number'),
  totalVirtualCores: DS.attr('number'),
  totalNodes: DS.attr('number'),
  lostNodes: DS.attr('number'),
  unhealthyNodes: DS.attr('number'),
  decommissionedNodes: DS.attr('number'),
  rebootedNodes: DS.attr('number'),
  activeNodes: DS.attr('number')
});