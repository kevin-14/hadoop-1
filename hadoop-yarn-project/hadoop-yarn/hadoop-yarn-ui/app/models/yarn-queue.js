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
  state: DS.attr('string'),
  userLimit: DS.attr('number'),
  userLimitFactor: DS.attr('number'),
  preemptionDisabled: DS.attr('number'),

  isLeafQueue: function() {
    var len = this.get("children.length");
    if (!len) {
      return true;
    }
    return len <= 0;
  }.property("children"),
});
