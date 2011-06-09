package be.cytomine.command.ontology

/**
 * Created by IntelliJ IDEA.
 * User: lrollus
 * Date: 16/03/11
 * Time: 15:57
 * To change this template use File | Settings | File Templates.
 */

import be.cytomine.command.UndoRedoCommand
import grails.converters.JSON
import be.cytomine.ontology.Ontology
import be.cytomine.command.AddCommand
import org.codehaus.groovy.grails.validation.exceptions.ConstraintException

class AddOntologyCommand extends AddCommand implements UndoRedoCommand {
  boolean saveOnUndoRedoStack = true;
  def execute() {
    log.info("Execute")
    Ontology newOntology=null
    try{
      def json = JSON.parse(postData)
      json.user = user.id
      newOntology = Ontology.createFromData(json)
      return super.validateAndSave(newOntology,["#ID#",json.name] as Object[])
    }catch(ConstraintException  ex){
      return [data : [ontology:newOntology,errors:newOntology.retrieveErrors()], status : 400]
    }catch(IllegalArgumentException ex){
      return [data : [ontology:null,errors:["Cannot save ontology:"+ex.toString()]], status : 400]
    }
  }

  def undo() {
    log.info("Undo")
    def ontologyData = JSON.parse(data)
    Ontology ontology = Ontology.get(ontologyData.id)
    ontology.delete(flush:true)
    String id = ontologyData.id
    return super.createUndoMessage(id,ontology,[ontologyData.id,ontologyData.name] as Object[]);
  }

  def redo() {
    log.info("Undo")
    def ontologyData = JSON.parse(data)
    def json = JSON.parse(postData)
    def ontology = Ontology.createFromData(ontologyData)
    log.info "data=" + data
    log.info "postData=" + postData
    log.info "ontologyData.id=" + ontologyData.id
    ontology.id = ontologyData.id
    Ontology.list().each{
      println it.id
    }
    log.info "ontology.id=" + ontology.id
    ontology.save(flush:true)
    log.info "ontology.id=" + ontology.id

    return super.createRedoMessage(ontology,[ontologyData.id,ontologyData.name] as Object[]);
  }

}
