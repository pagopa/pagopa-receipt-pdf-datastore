// Get Cosmos Client
import  Express  from "express";
import {createDocument, getDocumentByPartitionId, deleteDocument} from "../controllers/cosmosController.js";

const documentRouter = Express.Router();

documentRouter.post("/create", createDocument);
documentRouter.post("/getDocument", getDocumentByPartitionId);
documentRouter.post("/delete", deleteDocument);

export {
    documentRouter
}


