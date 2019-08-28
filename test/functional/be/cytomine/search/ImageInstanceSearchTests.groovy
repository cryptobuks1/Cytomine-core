package be.cytomine.search

/*
* Copyright (c) 2009-2019. Authors: see NOTICE file.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import be.cytomine.image.ImageInstance
import be.cytomine.project.Project
import be.cytomine.test.BasicInstanceBuilder
import be.cytomine.test.Infos
import be.cytomine.test.http.ImageInstanceAPI
import grails.converters.JSON
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject

class ImageInstanceSearchTests {


    //bounds
    void testGetBounds(){
        Project project = BasicInstanceBuilder.getProjectNotExist(true)
        ImageInstance img1 = BasicInstanceBuilder.getImageInstanceNotExist(project, true)
        img1.baseImage.width = 500
        img1.save()
        BasicInstanceBuilder.getUserAnnotationNotExist(img1.project, img1, true)
        img1 = img1.refresh()
        ImageInstance img2 = BasicInstanceBuilder.getImageInstanceNotExist(project, true)

        def result = ImageInstanceAPI.getBounds(project.id, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        def json = JSON.parse(result.data)

        assert json.containsKey("height")
        assert json.containsKey("width")
        assert json.containsKey("countImageAnnotations")
        assert json.containsKey("countImageJobAnnotations")
        assert json.containsKey("countImageReviewedAnnotations")
        assert json.containsKey("magnification")
        assert json.containsKey("resolution")
        assert json.resolution.containsKey("list")
        assert json.magnification.containsKey("list")
        assert json.containsKey("format")
        assert json.containsKey("mimeType")


        def widths = [img1.baseImage.width, img2.baseImage.width]
        def annots = [img1.countImageAnnotations, img2.countImageAnnotations]

        assert json.width.min == widths.min()
        assert json.width.max == widths.max()
        assert json.countImageAnnotations.min == annots.min()
        assert json.countImageAnnotations.max == annots.max()
    }

    //search
    void testGetSearch(){
        Project project = BasicInstanceBuilder.getProjectNotExist(true)
        ImageInstance img1 = BasicInstanceBuilder.getImageInstanceNotExist(project, true)
        img1.baseImage.width = 499
        img1.save()
        BasicInstanceBuilder.getUserAnnotationNotExist(img1.project, img1, true)
        ImageInstance img2 = BasicInstanceBuilder.getImageInstanceNotExist(project, true)

        def result = ImageInstanceAPI.listByProject(project.id, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        def json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        long size = json.size
        assert size == 2


        def searchParameters = [[operator : "lte", field : "width", value:500], [operator : "lte", field : "numberOfAnnotations", value:1000]]

        result = ImageInstanceAPI.listByProject(project.id, 0,0, searchParameters, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.size == 1

        searchParameters = [[operator : "gte", field : "numberOfAnnotations", value:1]]

        result = ImageInstanceAPI.listByProject(project.id, 0,0, searchParameters, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.size == 1

        searchParameters = [[operator : "lte", field : "width", value:100000], [operator : "lte", field : "numberOfAnnotations", value:10000]]

        result = ImageInstanceAPI.listByProject(project.id, 0,0, searchParameters, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.size == 2

        searchParameters = [[operator : "lte", field : "width", value:5000], [operator : "gte", field : "numberOfAnnotations", value:10000]]

        result = ImageInstanceAPI.listByProject(project.id, 0,0, searchParameters, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.size == 0
    }

    /*
* Sort to test : blindedName, instanceFilename, magnification, numberOfAnnotations, numberOfJobAnnotations, numberOfReviewedAnnotations
*/

    //pagination
    void testListImagesInstanceByProject() {
        BasicInstanceBuilder.getImageInstance()
        BasicInstanceBuilder.getImageInstanceNotExist(BasicInstanceBuilder.getImageInstance().project, true)
        def result = ImageInstanceAPI.listByProject(BasicInstanceBuilder.getProject().id, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        def json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        long size = json.size
        assert size > 1
        Long id1 = json.collection[0].id
        Long id2 = json.collection[1].id

        result = ImageInstanceAPI.listByProject(BasicInstanceBuilder.getProject().id, 1, 0, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.size == size
        assert json.collection.size() == 1
        assert json.collection[0].id == id1

        result = ImageInstanceAPI.listByProject(BasicInstanceBuilder.getProject().id, 1, 1, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.size == size
        assert json.collection.size() == 1
        assert json.collection[0].id == id2
    }

    void testListImagesInstanceByProjectWithLastActivity() {
        ImageInstance img = BasicInstanceBuilder.getImageInstanceNotExist(BasicInstanceBuilder.getProjectNotExist(true), true)
        BasicInstanceBuilder.getImageConsultationNotExist(img, true)
        BasicInstanceBuilder.getImageInstanceNotExist(img.project, true)

        def result = ImageInstanceAPI.listByProjectWithLastActivity(img.project.id, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        def json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        long size = json.size
        assert size > 1
        Long id1 = json.collection[0].id
        Long id2 = json.collection[1].id

        result = ImageInstanceAPI.listByProjectWithLastActivity(img.project.id, 1, 0, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.size == size
        assert json.collection.size() == 1
        assert json.collection[0].id == id1

        result = ImageInstanceAPI.listByProjectWithLastActivity(img.project.id, 1, 1, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.size == size
        assert json.collection.size() == 1
        assert json.collection[0].id == id2

    }

    void testListImagesInstanceByProjectLight() {
        Project project = BasicInstanceBuilder.getProjectNotExist(true)
        BasicInstanceBuilder.getImageInstanceNotExist(project, true)
        BasicInstanceBuilder.getImageInstanceNotExist(project, true)
        def result = ImageInstanceAPI.listByProjectLight(project.id, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        def json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        long size = json.size
        assert size > 1
        Long id1 = json.collection[0].id
        Long id2 = json.collection[1].id

        result = ImageInstanceAPI.listByProjectLight(project.id, 1, 0, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.collection.size() == 1
        assert size == json.size
        assert json.collection[0].id == id1

        result = ImageInstanceAPI.listByProjectLight(project.id, 1, 1, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.collection.size() == 1
        assert size == json.size
        assert json.collection[0].id == id2
    }


    void testListImagesInstanceByUserLight() {
        Project project = BasicInstanceBuilder.getProjectNotExist(true)
        BasicInstanceBuilder.getImageInstanceNotExist(project, true)
        BasicInstanceBuilder.getImageInstanceNotExist(project, true)
        def result = ImageInstanceAPI.listByUser(BasicInstanceBuilder.getUser1().id,Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        def json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        long size = json.size
        assert size > 1
        Long id1 = json.collection[0].id
        Long id2 = json.collection[1].id

        result = ImageInstanceAPI.listByUser(BasicInstanceBuilder.getUser1().id, 1, 0, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.collection.size() == 1
        assert size == json.size
        assert json.collection[0].id == id1

        result = ImageInstanceAPI.listByUser(BasicInstanceBuilder.getUser1().id, 1, 1, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json.collection instanceof JSONArray
        assert json.collection.size() == 1
        assert size == json.size
        assert json.collection[0].id == id2
    }


    void testListByProjectTree() {
        Project project = BasicInstanceBuilder.getProjectNotExist(true)
        BasicInstanceBuilder.getImageInstanceNotExist(project, true)
        BasicInstanceBuilder.getImageInstanceNotExist(project, true)
        def result = ImageInstanceAPI.listByProjectTree(project.id,Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        def json = JSON.parse(result.data)
        assert json instanceof JSONObject
        long size = json.size
        assert size > 1
        Long id1 = json.children[0].id
        Long id2 = json.children[1].id

        result = ImageInstanceAPI.listByProjectTree(project.id, 1, 0, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json instanceof JSONObject
        assert json.children.size() == 1
        assert size == json.size
        assert json.children[0].id == id1

        result = ImageInstanceAPI.listByProjectTree(project.id, 1, 1, Infos.SUPERADMINLOGIN, Infos.SUPERADMINPASSWORD)
        assert 200 == result.code
        json = JSON.parse(result.data)
        assert json instanceof JSONObject
        assert json.children.size() == 1
        assert size == json.size
        assert json.children[0].id == id2
    }

}