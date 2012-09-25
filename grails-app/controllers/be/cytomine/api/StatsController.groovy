package be.cytomine.api

import be.cytomine.ontology.Annotation
import be.cytomine.ontology.AnnotationTerm
import be.cytomine.ontology.Term
import be.cytomine.project.Project
import grails.orm.PagedResultList
import be.cytomine.security.SecUser
import be.cytomine.ontology.RelationTerm

class StatsController extends RestController {

    def annotationService
    def termService
    def algoAnnotationTermService
    def jobService
    def retrievalSuggestedTermJobService
    def retrievalEvolutionJobService
    def securityService
    def relationService

    def test = {

        PagedResultList annotations = Annotation.createCriteria().list(offset: 0, max: 5) {
               eq("project", Project.read(57))
               order 'created', 'desc'
           }

        def data = [:]
        data.records = annotations.totalCount
        data.total = Math.ceil(annotations.totalCount / 5) + "" //[100/10 => 10 page] [5/15
        data.rows = annotations.list
        responseSuccess(data)

    }


    def statUserAnnotations = {
        Project project = Project.read(params.id)
        if (project == null) responseNotFound("Project", params.id)
        def terms = Term.findAllByOntology(project.getOntology())
        if(terms.isEmpty()) {
            responseSuccess([])
            return
        }
        def nbAnnotationsByUserAndTerms = AnnotationTerm.createCriteria().list {
            inList("term", terms)
            join("annotation")
            createAlias("annotation", "a")
            projections {
                eq("a.project", project)
                groupProperty("a.user.id")
                groupProperty("term.id")
                count("term")
            }
        }

        Map<Long, Object> result = new HashMap<Long, Object>()
        project.userLayers().each { user ->
            def item = [:]
            item.id = user.id
            item.key = user.firstname + " " + user.lastname
            item.terms = []
            terms.each { term ->
                def t = [:]
                t.id = term.id
                t.name = term.name
                t.color = term.color
                t.value = 0
                item.terms << t
            }
            result.put(user.id, item)
        }
        nbAnnotationsByUserAndTerms.each { stat ->
            def user = result.get(stat[0])
            if(user) {
                user.terms.each {
                    if (it.id == stat[1]) {
                        it.value = stat[2]
                    }
                }
            }
        }
        responseSuccess(result.values())
    }

    def statUser = {
        Project project = Project.read(params.id)
        if (project == null) { responseNotFound("Project", params.id) }
        def userAnnotations = Annotation.createCriteria().list {
            eq("project", project)
            join("user")  //right join possible ? it will be sufficient
            projections {
                countDistinct('id')
                groupProperty("user.id")
            }
        }

        Map<Long, Object> result = new HashMap<Long, Object>()
        project.userLayers().each { user ->
            def item = [:]
            item.id = user.id
            item.key = user.firstname + " " + user.lastname
            item.value = 0
            result.put(item.id, item)
        }
        userAnnotations.each { item ->
            def user = result.get(item[1])
            if(user) user.value = item[0]
        }

        responseSuccess(result.values())
    }

    def statTerm = {
        Project project = Project.read(params.id)
        if (project == null) responseNotFound("Project", params.id)

        def terms = project.ontology.leafTerms()

        def results = Annotation.executeQuery('select t.term.id, count(t) from AnnotationTerm as t, Annotation as b where b.id=t.annotation.id and b.project = ? group by t.term.id', [project])

        def stats = [:]
        def color = [:]
        def ids = [:]
        def idsRevert = [:]
        def list = []

        //init list
        terms.each { term ->
                stats[term.name] = 0
                color[term.name] = term.color
                ids[term.name] = term.id
                idsRevert[term.id] = term.name
        }

        results.each { result ->
            def name = idsRevert[result[0]]
            if(name) stats[name]=result[1]
        }

        stats.each {
            list << ["id": ids.get(it.key), "key": it.key, "value": it.value, "color": color.get(it.key)]
        }
        responseSuccess(list)
    }

    /* Pour chaque terme, le nombre de slides dans lesquels ils ont été annotés. */
    def statTermSlide = {
        Project project = Project.read(params.id)
        if (project == null) responseNotFound("Project", params.id)
        def terms = Term.findAllByOntology(project.getOntology())
        def userLayers = project.userLayers()
        if(terms.isEmpty() || userLayers.isEmpty()) {
            responseSuccess([])
            return
        }
        def annotations = AnnotationTerm.createCriteria().list {
            inList("term", terms)
            inList("user", userLayers)
            join("annotation")
            createAlias("annotation", "a")
            projections {
                eq("a.project", project)
                groupProperty("a.image.id")
                groupProperty("term.id")
                count("term.id")
            }
        }
        Map<Long, Object> result = new HashMap<Long, Object>()
        terms.each { term ->
            def item = [:]
            item.id = term.id
            item.key = term.name
            item.value = 0
            result.put(item.id, item)
        }
        annotations.each { item ->
            def term = item[1]
            result.get(term).value++;
        }

        responseSuccess(result.values())
    }

    /*Pour chaque user, le nombre de slides dans lesquels ils ont fait des annotations.*/
    def statUserSlide = {
        Project project = Project.read(params.id)
        if (project == null) responseNotFound("Project", params.id)
        def terms = Term.findAllByOntology(project.getOntology())
        if(terms.isEmpty()) {
            responseSuccess([])
            return
        }
        def annotations = AnnotationTerm.createCriteria().list {
            inList("term", terms)
            join("annotation")
            createAlias("annotation", "a")
            projections {
                eq("a.project", project)
                groupProperty("a.image.id")
                groupProperty("a.user")
                count("a.user")
            }
        }
        Map<Long, Object> result = new HashMap<Long, Object>()
        project.userLayers().each { user ->
            def item = [:]
            item.id = user.id
            item.key = user.firstname + " " + user.lastname
            item.value = 0
            result.put(item.id, item)
        }
        annotations.each { item ->
            def user = result.get(item[1].id)
            if(user) user.value++;
        }

        responseSuccess(result.values())
    }
    
    
    def statAnnotationEvolution = {

        Project project = Project.read(params.id)
        if (project == null) responseNotFound("Project", params.id)
        int daysRange = params.daysRange!=null ? params.getInt('daysRange') : 1
        Term term = Term.read(params.getLong('term'))

        def data = []
        int count = 0;

        def annotations = null;
        if(!term) {
            annotations = Annotation.executeQuery("select a.created from Annotation a where a.project = ? and a.user.class = ? order by a.created desc", [project,"be.cytomine.security.User"])
        }
        else {
            log.info "Search on term " + term.name
            annotations = Annotation.executeQuery("select b.created from Annotation b where b.project = ? and b.id in (select x.annotation.id from AnnotationTerm x where x.term = ?) and b.user.class = ? order by b.created desc", [project,term,"be.cytomine.security.User"])
        }



        Date creation = project.created
        //stop today
        Date current = new Date()
        
        while(current.getTime()>=creation.getTime()) {
            def item = [:]
            while(count<annotations.size()) {
                if(annotations.get(count).getTime()<current.getTime()) break;
                count++;
            }

            item.date = current.getTime()
            item.size = annotations.size()-count;
            data << item

            Calendar cal = Calendar.getInstance();
            cal.setTime(current);
            cal.add(Calendar.DATE, -daysRange);
            current = cal.getTime();
        }
        responseSuccess(data)
    }
}
