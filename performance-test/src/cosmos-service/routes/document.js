// Get Cosmos Client
import  Express  from "express";
import {createDocument, getDocumentByEventId, deleteDocument} from "../controllers/cosmosController.js";

const documentRouter = Express.Router();

documentRouter.post("/create", createDocument);
documentRouter.post("/getDocument", getDocumentByEventId);
documentRouter.post("/delete", deleteDocument);

export {
    documentRouter
}


