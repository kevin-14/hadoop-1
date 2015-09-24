import Ember from 'ember';

export default Ember.Route.extend({
  model() {
    return Ember.RSVP.hash({
      //clusterMetrics : this.store.findAll('ClusterMetrics'),
      clusterInfo : this.store.findAll('ClusterInfo')
    });
  }
});