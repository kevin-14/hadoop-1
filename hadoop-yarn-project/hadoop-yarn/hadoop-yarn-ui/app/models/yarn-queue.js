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

  capacitiesBarChartData: function() {
    return [
      {
        label: "Absolute Capacity",
        value: this.get("name") == "root" ? 100 : this.get("absCapacity")
      },
      {
        label: "Absolute Used",
        value: this.get("name") == "root" ? this.get("usedCapacity") : this.get("absUsedCapacity")
      },
      {
        label: "Absolute Max Capacity",
        value: this.get("name") == "root" ? 100 : this.get("absMaxCapacity")
      }
    ]
  }.property("absCapacity", "absUsedCapacity", "absMaxCapacity"),

  userUsageDonutChartData: function() {
    return [
      {
        label: "Absolute Capacity",
        value: 1
      },
      {
        label: "Absolute Used",
        value: 2
      },
      {
        label: "Absolute Max Capacity",
        value: 3
      }
    ]
  }.property()
});
