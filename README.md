graphwalker-service
===================

GraphWalker as a service is a WebSocket server, that allows multiple clients to connect. Every client can send 1 or several models to the service, and then ask GraphWalker to generate an online test sequence.
 
The data sent back and forth between a client and server are JSON data. The JSON data contains a header and then a payload.


### Requests sent to the server
#### Load a model
GraphWalker needs at least 1 model. The first model sent, will be the model where the execution starts. More than one model can be sent. The request also needs a payload, which is the model, as a di-graph in JSON notation.

Client request
~~~json
{
    "type": "loadModel",
    "model": {
        "name": "Small model",
        "generator": "random(edge_coverage(100))",
        "vertices": [
            {
                "name": "v_VerifySomeAction",
                "id": "n0"
            },
            {
                "name": "v_VerifySomeOtherAction",
                "id": "n1"
            }
        ],
        "edges": [
            {
                "name": "e_FirstAction",
                "id": "e0",
                "dstVertexId": "n0",
                "startElement": "true"
            },
            {
                "name": "e_AnotherAction",
                "id": "e1",
                "srcVertexId": "n0",
                "dstVertexId": "n1"
            },
            {
                "name": "e_SomeOtherAction",
                "id": "e2",
                "srcVertexId": "n1",
                "dstVertexId": "n1"
            },
            {
                "name": "e_SomeOtherAction",
                "id": "e3",
                "srcVertexId": "n1",
                "dstVertexId": "n0"
            }
        ]
    }
}
~~~
Server response
~~~json
{
    "type": "loadModel",
    "success": boolean,
    "msg": "If success is false, an message will returned"
}
~~~

All fileds are mandatory, with 2 exceptions.
* ***startElement*** points out at which element (edge or vertex) the execution should start. This is mandatory, but only for ***one*** of the elements, and only for the ***first*** model. 
 
#### Start
Sets GraphWalker in a ready state. No more models can be loaded. 

Client request
~~~json
{
    "type": "start"
}
~~~
Server response
~~~json
{
    "type": "start",
    "success": boolean,
    "msg": "If success is false, a message will returned",
}
~~~

#### Get next step
Asks GraphWalker to execute one step in the execution. The name and id of the next step will be returned.

Client request
~~~json
{
    "type": "getNext"
}
~~~
Server response
~~~json
{
    "type": "getNext",
    "id": "Element id",
    "name": "Element name"
    "success": boolean,
    "msg": "If success is false, a message will returned",
}
~~~

#### Has next step
Asks GraphWalker if all stop conditons for all model(s) have been fullfiled. As long as there are steps to execute, true will be returned.

Client request
~~~json
{
    "type": "hasNext"
}
~~~
Server response
~~~json
{
    "type": "hasNext",
    "hasNext": boolean,
    "success": boolean,
    "msg": "If success is false, a message will returned"
}
~~~

#### Get data
Asks GraphWalker to return the key andn values of any data from the model

Client request
~~~json
{
    "type": "getData"
}
~~~
Server response
~~~json
{
    "type": "getData",
    "success": boolean,
    "msg": "If success is false, a message will returned",
    "data": {
        :
        :
    }
}
~~~

#### Restart
Stops any execution, clearsall data and set GraphWalker in the inital state.

Client request
~~~json
{
    "type": "restart"
}
~~~
Server response
~~~json
{
    "type": "restart",
    "success": boolean,
    "msg": "If success is false, a message will returned"
}
~~~
