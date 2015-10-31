import Ember from 'ember';

export default Ember.Route.extend({
  model(param) {
    return Ember.RSVP.hash({
      selected : param.queue_name,
      queues: this.store.findAll('yarnQueue'),
      selectedQueue : undefined
    });
  },

  afterModel(model) {
    model.selectedQueue = this.store.peekRecord('yarnQueue', model.selected);

    console.log(model.selectedQueue);
  }
});
