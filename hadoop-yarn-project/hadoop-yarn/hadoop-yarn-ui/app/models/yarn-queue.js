import DS from 'ember-data';

export default DS.Model.extend({
  name: DS.attr('string'),
  children: DS.attr('array'),
  parent: DS.attr('string'),
  capacity: DS.attr('number'),
  maxCapacity: DS.attr('number'),
  usedCapacity: DS.attr('number'),
  absCapacity: DS.attr('number'),
  absMaxCapacity: DS.attr('number'),
  absUsedCapacity: DS.attr('number'),
  status: DS.attr('string'),
});
